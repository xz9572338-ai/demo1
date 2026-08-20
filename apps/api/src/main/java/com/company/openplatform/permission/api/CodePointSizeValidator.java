package com.company.openplatform.permission.api;
import jakarta.validation.ConstraintValidator; import jakarta.validation.ConstraintValidatorContext;
public class CodePointSizeValidator implements ConstraintValidator<CodePointSize,String>{private int min,max;public void initialize(CodePointSize a){min=a.min();max=a.max();}public boolean isValid(String v,ConstraintValidatorContext c){if(v==null)return true;int n=v.codePointCount(0,v.length());return n>=min&&n<=max;}}
