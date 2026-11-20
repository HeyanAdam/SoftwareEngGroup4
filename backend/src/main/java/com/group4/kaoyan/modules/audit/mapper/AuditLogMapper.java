package com.group4.kaoyan.modules.audit.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.group4.kaoyan.modules.audit.entity.AuditLogRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AuditLogMapper {

    int insert(AuditLogRecord record);

    List<AuditLogRecord> selectByModule(@Param("module") String module,
                                        @Param("action") String action,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        @Param("offset") int offset,
                                        @Param("size") int size);

    long countByModule(@Param("module") String module,
                       @Param("action") String action,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to);
}
