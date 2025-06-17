package com.sttweb.sttweb.logging;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogActivity {
  String type();       // 대분류 (record, branch, member 등)
  String activity();   // 활동명 (조회, 청취, 다운로드 등)
  String contents() default "";  // 상세내용 (SpEL 가능)
  String dir() default "";       // 경로/파일 정보
}