package top.ryuu64.contract;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AutoService(Processor.class)
@SupportedAnnotationTypes("top.ryuu64.contract.MethodContract")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class MethodContractProcessor extends AbstractProcessor {
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> metaAnnotatedAnnotations = roundEnv.getElementsAnnotatedWith(MethodContract.class);

        for (Element annotationElement : metaAnnotatedAnnotations) {
            if (annotationElement.getKind() != ElementKind.ANNOTATION_TYPE) {
                continue;
            }

            TypeElement annotationType = (TypeElement) annotationElement;
            MethodContract contract = annotationType.getAnnotation(MethodContract.class);
            if (contract == null) {
                continue;
            }

            Set<? extends Element> elementsAnnotatedWithContract = roundEnv.getElementsAnnotatedWith(annotationType);
            for (Element element : elementsAnnotatedWithContract) {
                if (!isValidClass(element)) {
                    continue;
                }

                processContract(element, contract);
            }
        }
        return true;
    }

    private boolean isValidClass(Element element) {
        if (element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE) {
            return true;
        }

        messager.printMessage(
                Diagnostic.Kind.ERROR,
                "@MethodContract can only be applied to a class or interface.",
                element
        );
        return false;
    }

    private void processContract(Element element, MethodContract contract) {
        TypeElement classElement = (TypeElement) element;
        List<Modifier> requiredModifiers = Arrays.stream(contract.modifiers()).collect(Collectors.toList());
        String requiredMethodName = contract.methodName();
        List<? extends TypeMirror> requiredParameterType;
        try {
            contract.parameterTypes();
            requiredParameterType = Collections.emptyList();
        } catch (MirroredTypesException exception) {
            requiredParameterType = exception.getTypeMirrors();
        }

        TypeMirror requiredReturnType;
        try {
            contract.returnType();
            requiredReturnType = element.asType();
        } catch (MirroredTypeException exception) {
            requiredReturnType = exception.getTypeMirror();
        }

        if (containsMatchingMethod(classElement, requiredModifiers, requiredMethodName, requiredParameterType, requiredReturnType)) {
            return;
        }

        printErrorMessage(classElement, contract.modifiers(), requiredReturnType, requiredMethodName, requiredParameterType);
    }

    private void printErrorMessage(
            TypeElement classElement,
            Modifier[] modifiers,
            TypeMirror requiredReturnType,
            String requiredMethodName,
            List<? extends TypeMirror> requiredParameterType
    ) {
        String modifiersString = Arrays.stream(modifiers)
                .map(Modifier::toString)
                .collect(Collectors.joining(" "));
        String paramTypesString = requiredParameterType.stream()
                .map(TypeMirror::toString)
                .collect(Collectors.joining(", "));
        String errorMessage = String.format(
                "Class '%s' must implement a method: `%s %s %s(%s)`.",
                classElement.getSimpleName(),
                modifiersString,
                requiredReturnType.toString(),
                requiredMethodName,
                paramTypesString
        );
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, errorMessage, classElement);
    }

    private boolean containsMatchingMethod(
            TypeElement classElement,
            List<Modifier> requiredModifiers,
            String requiredMethodName,
            List<? extends TypeMirror> requiredParamTypes,
            TypeMirror requiredReturnType
    ) {
        Types typeUtils = processingEnv.getTypeUtils();
        for (Element enclosedElement : classElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement element = (ExecutableElement) enclosedElement;
            if (!hasModifiers(element, requiredModifiers)) {
                continue;
            }

            if (!hasReturnType(element, requiredReturnType, typeUtils)) {
                continue;
            }

            if (!hasSimpleName(element, requiredMethodName)) {
                continue;
            }

            if (hasParameterTypes(element, requiredParamTypes, typeUtils)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasModifiers(ExecutableElement element, List<Modifier> requiredModifiers) {
        return element.getModifiers().containsAll(requiredModifiers);
    }

    private static boolean hasReturnType(ExecutableElement element, TypeMirror requiredReturnType, Types typeUtils) {
        return typeUtils.isSameType(element.getReturnType(), requiredReturnType);
    }

    private static boolean hasSimpleName(ExecutableElement element, String name) {
        return element.getSimpleName().toString().equals(name);
    }

    private static boolean hasParameterTypes(
            ExecutableElement element,
            List<? extends TypeMirror> requiredTypes,
            Types typeUtils
    ) {
        List<TypeMirror> types = element.getParameters().stream()
                .map(VariableElement::asType)
                .collect(Collectors.toList());
        if (types.size() != requiredTypes.size()) {
            return false;
        }

        for (int i = 0; i < requiredTypes.size(); i++) {
            TypeMirror methodParamType = types.get(i);
            TypeMirror requiredParamType = requiredTypes.get(i);

            if (!typeUtils.isSameType(methodParamType, requiredParamType)) {
                return false;
            }
        }
        return true;
    }
}
