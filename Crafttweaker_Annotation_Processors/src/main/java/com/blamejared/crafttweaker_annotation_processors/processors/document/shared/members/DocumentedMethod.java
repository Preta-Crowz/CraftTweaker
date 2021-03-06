package com.blamejared.crafttweaker_annotation_processors.processors.document.shared.members;

import com.blamejared.crafttweaker_annotation_processors.processors.document.CrafttweakerDocumentationPage;
import com.blamejared.crafttweaker_annotation_processors.processors.document.shared.Writable;
import com.blamejared.crafttweaker_annotation_processors.processors.document.shared.types.DocumentedType;
import com.blamejared.crafttweaker_annotation_processors.processors.document.shared.util.CommentUtils;
import org.openzen.zencode.java.ZenCodeType;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import java.io.PrintWriter;
import java.util.List;

public class DocumentedMethod implements Writable {

    private final String name;
    private final List<DocumentedParameter> parameterList;
    private final CrafttweakerDocumentationPage containingPage;
    private final String callee; //full class name if static, else my[Type] or content of docParam this
    private final String docComment;
    private final DocumentedType returnType;

    public DocumentedMethod(CrafttweakerDocumentationPage containingPage, String name, List<DocumentedParameter> parameterList, String callee, String docComment, DocumentedType returnType) {
        this.containingPage = containingPage;
        this.name = name;
        this.parameterList = parameterList;
        this.callee = callee;
        this.docComment = docComment;
        this.returnType = returnType;
    }

    public static DocumentedMethod convertMethod(CrafttweakerDocumentationPage containingPage, ExecutableElement method, ProcessingEnvironment environment, boolean isExpansion) {
        final ZenCodeType.Method methodAnnotation = method.getAnnotation(ZenCodeType.Method.class);
        if (methodAnnotation == null) {
            //Nani?
            environment.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR, "Internal error: method has not Method Annotation", method);
            return null;
        }

        if (!method.getModifiers().contains(Modifier.PUBLIC)) {
            environment.getMessager().printMessage(Diagnostic.Kind.ERROR, "Methods need to be public!", method);
            return null;
        }

        final String name = methodAnnotation.value()
                .isEmpty() ? method.getSimpleName()
                .toString() : methodAnnotation.value();

        final List<DocumentedParameter> parameterList = DocumentedParameter.getMethodParameters(method, environment, isExpansion);
        final boolean isStatic = method.getModifiers().contains(Modifier.STATIC);
        if (isExpansion && !isStatic) {
            environment.getMessager().printMessage(Diagnostic.Kind.ERROR, "Expansion methods must be static!", method);
        }

        final String docComment = environment.getElementUtils().getDocComment(method);


        String callee = null;
        if (!isExpansion && isStatic) {
            callee = containingPage.getZSName();
        } else if (isExpansion) {
            final String docParam_this = CommentUtils.joinDocAnnotation(docComment, "docParam " + method.getParameters()
                    .get(0)
                    .getSimpleName(), environment)
                    .trim();
            if (!docParam_this.isEmpty()) {
                callee = docParam_this;
            }
        }
        if (callee == null && docComment != null) {
            final String docParam_this = CommentUtils.joinDocAnnotation(docComment, "docParam this", environment)
                    .trim();
            if (!docParam_this.isEmpty()) {
                callee = docParam_this;
            }
        }
        if (callee == null) {
            callee = containingPage.getDocParamThis();
        }

        final DocumentedType returnType = method.getReturnType()
                .getKind() == TypeKind.VOID ? null : DocumentedType.fromTypeMirror(method.getReturnType(), environment);


        return new DocumentedMethod(containingPage, name, parameterList, callee, CommentUtils.formatDocCommentForDisplay(method, environment), returnType);

    }

    public String getName() {
        return name;
    }

    public DocumentedMethod withCallee(String c) {
        if (c != null) {
            return new DocumentedMethod(containingPage, name, parameterList, c, docComment, returnType);
        }
        return this;
    }

    @Override
    public void write(PrintWriter writer) {
        writer.println();

        if (docComment != null) {
            writer.println(docComment);
            writer.println();
        }
        if (returnType != null) {
            writer.println("Returns " + returnType.getClickableMarkdown());
            writer.println();
        }
        writer.println("```zenscript");
        DocumentedParameter.printAllCalls(callee + "." + name, parameterList, writer);
        writer.println("```");
        writer.println();

        if (!parameterList.isEmpty()) {
            DocumentedParameter.printTable(parameterList, writer);
            writer.println();
        }
    }

    public CrafttweakerDocumentationPage getContainingPage() {
        return containingPage;
    }

    public List<DocumentedParameter> getParameterList() {
        return parameterList;
    }
}
