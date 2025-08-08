// src/main/java/com/sttweb/sttweb/logging/LogMsgUtil.java
package com.sttweb.sttweb.logging;

import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.service.TmemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("logMsgUtil")
@RequiredArgsConstructor
public class LogMsgUtil {

  private final TmemberService tmemberService;

  public String grant(String actorUserId, Integer targetSeq, Integer level) {
    Info t = tmemberService.getMyInfoByMemberSeq(targetSeq);
    return String.format(
        "사용자 %s이(가) 사용자 %s에게 다른 사용자에 관한 레벨 %d 권한을 부여",
        actorUserId, t.getUserId(), level
    );
  }

  public String revoke(String actorUserId, Integer targetSeq) {
    Info t = tmemberService.getMyInfoByMemberSeq(targetSeq);
    return String.format(
        "사용자 %s이(가) 사용자 %s에게 권한 회수",
        actorUserId, t.getUserId()
    );
  }
}
