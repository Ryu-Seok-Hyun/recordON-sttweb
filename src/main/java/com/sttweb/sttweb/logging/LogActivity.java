// src/main/java/com/sttweb/sttweb/logging/LogActivity.java
package com.sttweb.sttweb.logging;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogActivity {
  String type();       // 대분류
  String activity();   // 활동명
  String contents() default "";  // 상세내용
  String dir() default "";       // 파일/경로
}
