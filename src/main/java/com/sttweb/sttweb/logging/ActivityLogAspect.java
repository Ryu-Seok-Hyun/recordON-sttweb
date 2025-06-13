package com.sttweb.sttweb.logging;

import com.sttweb.sttweb.dto.TactivitylogDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.service.TactivitylogService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TbranchService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class ActivityLogAspect {

  private static final Logger log = LoggerFactory.getLogger(ActivityLogAspect.class);

  // SpEL 평가기 & 파라미터 이름 추출기
  private final SpelExpressionParser parser = new SpelExpressionParser();
  private final ParameterNameDiscoverer pd = new DefaultParameterNameDiscoverer();

  private final TactivitylogService logService;
  private final TmemberService memberSvc;
  private final TbranchService branchSvc;

  private static final DateTimeFormatter FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Around("@annotation(logActivity)")
  public Object around(ProceedingJoinPoint jp, LogActivity logActivity) throws Throwable {
    // 1) 실제 비즈니스 로직 수행
    Object result = jp.proceed();

    // 2) HTTP 요청이 아닐 경우 로깅 생략
    ServletRequestAttributes sa =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (sa == null) return result;
    HttpServletRequest req = sa.getRequest();

    // 3) 현재 인증 정보
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    // operator (로그 남기는 주체) 정보 추출
    String operatorUserId   = "";
    String operatorWorkerId = "anonymous";
    int    operatorSeq      = 0;

    if (auth != null
        && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken)) {
      // JWT 인증된 사용자
      operatorUserId = auth.getName();
      Info me = memberSvc.getMyInfoByUserId(operatorUserId);
      operatorWorkerId = me.getUserId();    // 실제 userId
      operatorSeq      = me.getMemberSeq(); // memberSeq
    }

    // 4) 기본 user_id 로 기록할 값 결정
    String userIdToLog = operatorUserId;

    // - record 타입이면 첫 파라미터(내선번호 등) 를 user_id 로 기록
    if ("record".equals(logActivity.type())) {
      Object[] args = jp.getArgs();
      if (args != null && args.length > 0 && args[0] != null) {
        userIdToLog = args[0].toString();
      }
    }

    // - 로그인/회원가입 호출 시, 로그아웃 전 세션 정보가 없으므로
    //   파라미터(LoginRequest, SignupRequest) 에서 userId 를 꺼내도록 처리
    if (operatorUserId.isEmpty()) {
      for (Object arg : jp.getArgs()) {
        if (arg instanceof LoginRequest) {
          userIdToLog = ((LoginRequest) arg).getUserId();
          break;
        }
        if (arg instanceof SignupRequest) {
          userIdToLog = ((SignupRequest) arg).getUserId();
          break;
        }
      }
      operatorWorkerId = userIdToLog;
    }

    // 5) branch 정보에서 사설IP/공인IP 가져오기
    int    branchSeq   = 0;
    int    memberSeq   = operatorSeq;
    int    employeeId  = 0;
    String companyName = "";
    String pbIp = "";
    String pIp  = "";

    try {
      Info userInfo = memberSvc.getMyInfoByUserId(userIdToLog);
      branchSeq = userInfo.getBranchSeq();
      if (branchSeq > 0) {
        var branch = branchSvc.findById(branchSeq);
        if (branch != null) {
          companyName = branch.getCompanyName();
          pbIp = branch.getPbIp();
          pIp  = branch.getPIp();
        }
      }
    } catch (Exception ex) {
      // branch 조회 실패 시, 빈 값(혹은 기존방식 사용) 유지
    }

    // 로그인/회원가입 등 branch 없는 경우, WAS에서 받은 IP 기록(기존 로직 유지)
    if (pbIp == null || pbIp.isBlank()) {
      // X-Forwarded-For: 실제 클라이언트의 공인IP + 프록시 체인
      String xff = req.getHeader("X-Forwarded-For");
      String remoteIp = req.getRemoteAddr();   // 사설IP (WAS가 인식하는 IP)
      if (xff != null && !xff.isBlank()) {
        pbIp = xff.split(",")[0].trim();
      } else {
        pbIp = remoteIp;
      }
      pIp = remoteIp;
    }

    // SpEL 평가용 컨텍스트 준비
    MethodSignature sig = (MethodSignature) jp.getSignature();
    Method method = sig.getMethod();
    Object[] args = jp.getArgs();
    StandardEvaluationContext ctx = new StandardEvaluationContext();
    // args → p0, p1, …
    for (int i = 0; i < args.length; i++) {
      ctx.setVariable("p" + i, args[i]);
    }
    // 파라미터 이름 → 값
    String[] names = pd.getParameterNames(method);
    if (names != null) {
      for (int i = 0; i < names.length; i++) {
        ctx.setVariable(names[i], args[i]);
      }
    }
    ctx.setVariable("principal", auth != null ? auth.getPrincipal() : null);

    // contents 평가
    String expr = logActivity.contents();
    String contents = "";
    if (expr != null && expr.contains("#{")) {
      try {
        contents = parser
            .parseExpression(expr, new TemplateParserContext())
            .getValue(ctx, String.class);
      } catch (EvaluationException ex) {
        contents = expr;
      }
    } else if (expr != null) {
      contents = expr;
    }

    // dir 평가
    String dirExpr = logActivity.dir();
    String dir = "";
    if (dirExpr != null && dirExpr.contains("#{")) {
      try {
        dir = parser
            .parseExpression(dirExpr, new TemplateParserContext())
            .getValue(ctx, String.class);
      } catch (EvaluationException ex) {
        dir = dirExpr;
      }
    } else if (dirExpr != null) {
      dir = dirExpr;
    }

    // DTO 생성 및 저장
    TactivitylogDto dto = TactivitylogDto.builder()
        .type(logActivity.type())
        .activity(logActivity.activity())
        .contents(contents)
        .dir(dir)
        .branchSeq(branchSeq)
        .companyName(companyName)
        .memberSeq(memberSeq)
        .userId(userIdToLog)
        .employeeId(employeeId)
        .pbIp(pbIp)
        .pvIp(pIp)
        .crtime(LocalDateTime.now().format(FMT))
        .workerSeq(operatorSeq)
        .workerId(operatorWorkerId)
        .build();

    log.info("[ActivityLog DTO] {}", dto);
    logService.createLog(dto);

    return result;
  }
}
