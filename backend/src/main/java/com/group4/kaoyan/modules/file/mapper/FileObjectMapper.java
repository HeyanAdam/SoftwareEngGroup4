package com.group4.kaoyan.modules.file.mapper;

import com.group4.kaoyan.modules.file.entity.FileObject;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FileObjectMapper {

    int insert(FileObject file);

    FileObject selectById(@Param("id") Long id);

    FileObject selectByIdForOwner(@Param("id") Long id, @Param("ownerUserId") Long ownerUserId);

    FileObject selectByOwnerAndHash(@Param("ownerUserId") Long ownerUserId, @Param("sha256") String sha256);

    FileObject selectByOwnerAndKey(@Param("ownerUserId") Long ownerUserId, @Param("objectKey") String objectKey);

    List<FileObject> selectPage(@Param("ownerUserId") Long ownerUserId,
                                @Param("scene") String scene,
                                @Param("offset") int offset,
                                @Param("size") int size);

    long countPage(@Param("ownerUserId") Long ownerUserId, @Param("scene") String scene);

    List<FileObject> selectByIds(@Param("ownerUserId") Long ownerUserId, @Param("ids") List<Long> ids);

    int logicDelete(@Param("id") Long id, @Param("ownerUserId") Long ownerUserId);

    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
