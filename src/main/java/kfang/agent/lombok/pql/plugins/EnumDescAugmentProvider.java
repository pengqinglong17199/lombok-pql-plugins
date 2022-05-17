package kfang.agent.lombok.pql.plugins;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * EnumDescFactory
 *
 * @author pengqinglong
 * @since 2022/5/12
 */
public class EnumDescAugmentProvider extends PsiAugmentProvider {

    private static final Logger log = Logger.getInstance(EnumDescAugmentProvider.class.getName());
    public static final String ENUM_DESC = "kfang.agent.feature.lombok.pql.annotations.EnumDesc";

    public EnumDescAugmentProvider() {
        log.debug("EnumDescAugmentProvider created");
    }

        @Override
    @Deprecated
    protected <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type) {
        return getAugments(element, type, null);
    }

    @Override
    protected <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                             @NotNull Class<Psi> type,
                                                             @Nullable String nameHint) {
        final List<Psi> emptyResult = Collections.emptyList();

        if ((type != PsiMethod.class) || !(element instanceof PsiExtensibleClass)) {
            return emptyResult;
        }

        final PsiClass psiClass = (PsiClass) element;
        if (psiClass.isAnnotationType() || psiClass.isInterface()) {
            return emptyResult;
        }
        PsiField[] fields = psiClass.getFields();
        if(fields.length == 0){
            return emptyResult;
        }

        List<Psi> list = new ArrayList<>();
        Map<Boolean, List<PsiField>> fieldMap = Arrays.stream(fields).collect(Collectors.groupingBy(field -> field.getAnnotation(ENUM_DESC) != null));

        this.handleField(fieldMap.get(true), psiClass, list);

        this.handleClass(fieldMap.get(false), psiClass, list);

        return list;
    }


    private <Psi extends PsiElement> void handleClass(List<PsiField> list, PsiClass psiClass, List<Psi> result) {
        PsiAnnotation annotation = psiClass.getAnnotation(ENUM_DESC);
        if(annotation == null){
            return;
        }

        for (PsiField field : list) {
            if(!this.isEnum(field)){
                continue;
            }

            String name = String.format("get%s%s", upperCase(field.getName()), "Desc");
            LightMethodBuilder method = createMethod(psiClass, field, name);
            result.add((Psi)method);
        }
    }

    private <Psi extends PsiElement>  void handleField(List<PsiField> list, PsiClass psiClass, List<Psi> result) {
        if(list == null || list.size() == 0){
            return;
        }

        for (PsiField field : list) {

            if(!this.isEnum(field)){
                continue;
            }

            PsiAnnotation annotation = field.getAnnotation(ENUM_DESC);

            String fieldName = field.getName();

            List<JvmAnnotationAttribute> attributes = annotation.getAttributes();
            if(attributes.size() == 0){
                String name = String.format("get%s%s", upperCase(fieldName), "Desc");
                LightMethodBuilder method = createMethod(psiClass, field, name);
                if(method != null){
                    result.add((Psi)method);
                }
                continue;
            }

            JvmAnnotationAttributeValue attributeValue = attributes.get(0).getAttributeValue();
            if(attributeValue instanceof JvmAnnotationConstantValue){
                String value = (String)((JvmAnnotationConstantValue) attributeValue).getConstantValue();

                String name = String.format("get%s%s", upperCase(fieldName), upperCase(value));
                LightMethodBuilder method = createMethod(psiClass, field, name);
                result.add((Psi)method);

                continue;
            }

            if (attributeValue instanceof JvmAnnotationArrayValue) {
                List<JvmAnnotationAttributeValue> values = ((JvmAnnotationArrayValue) attributeValue).getValues();
                for (JvmAnnotationAttributeValue tempValue : values) {
                    if(tempValue instanceof JvmAnnotationConstantValue){
                        String value = (String)((JvmAnnotationConstantValue) tempValue).getConstantValue();
                        String name = String.format("get%s%s", upperCase(fieldName), upperCase(value));
                        LightMethodBuilder method = createMethod(psiClass, field, name);
                        result.add((Psi)method);
                    }
                }
            }
        }
    }

    private boolean isEnum(PsiField field) {
        PsiType type1 = field.getType();
        PsiType[] superTypes = type1.getSuperTypes();
        if(superTypes.length == 0){
            return false;
        }

        if (superTypes[0].getPresentableText().startsWith("Enum")) {
            return true;
        }
        return false;
    }

    private LightMethodBuilder createMethod(PsiClass psiClass, PsiField field, String name) {
        PsiElement context = psiClass.getContext();

        assert context != null;
        if(context.getText().contains(String.format("String %s", name))){
            return null;
        }
        LightMethodBuilder methodBuilder = new LightMethodBuilder(psiClass.getManager(), JavaLanguage.INSTANCE, name);
        methodBuilder.addModifier(PsiModifier.PUBLIC);
        methodBuilder.setContainingClass(psiClass);
        methodBuilder.setNavigationElement(field);
        methodBuilder.setMethodReturnType("java.lang.String");

        return methodBuilder;
    }

    /**
     * 字符串首字母大写
     */
    protected String upperCase(String str) {
        String suffix;
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        bytes[0] = (byte) (bytes[0] - 32);
        suffix = new String(bytes);
        return suffix;
    }
}