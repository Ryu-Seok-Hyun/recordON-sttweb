package com.sttweb.sttweb;

import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.service.TbranchService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// utils/BranchResolver.java – 새 파일
@Component
@RequiredArgsConstructor
public class BranchResolver {

  private final TbranchService branchSvc;

  /** 요청이 들어온 Tomcat(또는 Nginx) ↔ DB 의 branch_seq 매핑 */
  public Integer resolveServerBranchSeq(HttpServletRequest req) {

    String host = Optional.ofNullable(req.getHeader("X-Forwarded-Host"))
        .orElse(req.getHeader("Host"));            // 프록시 뒤에 있으면 X-Forwarded-Host
    String ip   = (host != null) ? host.split(":")[0] : req.getLocalAddr();
    int    port = req.getServerPort();

    return branchSvc.findBypIp(ip).filter(b -> b.getPPort().equals(port))
        .or(() -> branchSvc.findByPbIp(ip).filter(b -> b.getPbPort().equals(port)))
        .map(TbranchEntity::getBranchSeq)
        .orElse(0);   // 0 == 본사 서버
  }
}
