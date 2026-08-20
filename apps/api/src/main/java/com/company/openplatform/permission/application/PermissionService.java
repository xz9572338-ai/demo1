package com.company.openplatform.permission.application;

import com.company.openplatform.application.application.ApplicationAccessService;
import com.company.openplatform.permission.domain.PermissionCode;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {
    private final JdbcTemplate jdbc; private final ApplicationAccessService applications; private final Clock clock;
    public PermissionService(JdbcTemplate jdbc, ApplicationAccessService applications, Clock clock) { this.jdbc=jdbc; this.applications=applications; this.clock=clock; }
    public List<View> list(String accountId, String applicationId) {
        long id=applications.requireOwnedActive(accountId, applicationId).id();
        Map<String,View> current=new HashMap<>();
        jdbc.query("select p.permission_code,p.status,r.created_at,p.updated_at,p.rejection_reason from application_permissions p join permission_requests r on r.id=p.current_request_id where p.application_id=?",
                (rs,n) -> view(PermissionCode.valueOf(rs.getString(1)),rs.getString(2),rs.getTimestamp(3).toInstant(),rs.getTimestamp(4).toInstant(),rs.getString(5)), id)
                .forEach(view -> current.put(view.code().name(), view));
        return Arrays.stream(PermissionCode.values()).map(code -> current.getOrDefault(code.name(),view(code,"NOT_APPLIED",null,null,null))).toList();
    }
    @Transactional
    public List<View> submit(String accountId, String applicationId, List<PermissionCode> codes, String reason) {
        if(codes==null||codes.isEmpty()||codes.size()>3||codes.stream().anyMatch(Objects::isNull)||new HashSet<>(codes).size()!=codes.size()) throw new IllegalArgumentException("permissions invalid");
        String normalized=reason==null?"":reason.strip();
        if(normalized.isEmpty()||normalized.codePoints().allMatch(cp->Character.isWhitespace(cp)||Character.isSpaceChar(cp))||normalized.codePointCount(0,normalized.length())>500) throw new IllegalArgumentException("reason invalid");
        long appId=applications.requireOwnedActive(accountId, applicationId).id(); Instant now=clock.instant(); List<View> result=new ArrayList<>();
        for(PermissionCode code:codes){
            try {
                List<String> states=jdbc.query("select status from application_permissions where application_id=? and permission_code=? for update",(rs,n)->rs.getString(1),appId,code.name());
                if(!states.isEmpty()&&"PENDING_REVIEW".equals(states.getFirst())) throw new PermissionConflictException("PERMISSION_REQUEST_ALREADY_PENDING");
                if(!states.isEmpty()&&"APPROVED".equals(states.getFirst())) throw new PermissionConflictException("PERMISSION_ALREADY_APPROVED");
                String publicId=UUID.randomUUID().toString();
                jdbc.update("insert into permission_requests(public_id,application_id,permission_code,reason,status,created_at,updated_at) values(?,?,?,?,'PENDING_REVIEW',?,?)",publicId,appId,code.name(),normalized,now,now);
                Long requestId=jdbc.queryForObject("select id from permission_requests where public_id=?",Long.class,publicId);
                if(states.isEmpty()) jdbc.update("insert into application_permissions(application_id,permission_code,status,current_request_id,updated_at) values(?,?,'PENDING_REVIEW',?,?)",appId,code.name(),requestId,now);
                else jdbc.update("update application_permissions set status='PENDING_REVIEW',current_request_id=?,internal_customer_scope=null,rejection_reason=null,updated_at=?,version=version+1 where application_id=? and permission_code=? and status='REJECTED'",requestId,now,appId,code.name());
                result.add(view(code,"PENDING_REVIEW",now,now,null));
            } catch (DuplicateKeyException | CannotAcquireLockException conflict) {
                throw new PermissionConflictException("PERMISSION_REQUEST_ALREADY_PENDING");
            }
        } return result;
    }
    public boolean approved(long applicationId, PermissionCode code) { Integer n=jdbc.queryForObject("select count(*) from application_permissions where application_id=? and permission_code=? and status='APPROVED'",Integer.class,applicationId,code.name()); return n!=null&&n==1; }
    private View view(PermissionCode c,String status,Instant submitted,Instant updated,String rejection){return new View(c,c.displayName,c.purpose,c.dataScope,c.sensitiveNotice,status,submitted,updated,rejection);}
    public record View(PermissionCode code,String name,String purpose,String dataScope,String sensitiveNotice,String status,Instant submittedAt,Instant updatedAt,String rejectionReason){}
}
