package com.lanxinai.data.paltform.ducklake.dao.mybatis;

import com.lanxinai.data.paltform.ducklake.domain.DemoRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DemoRecordMapper {

    @Select("""
            SELECT id, batch_id, name, quantity, amount, status, remark, created_at, updated_at
            FROM ${table}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<DemoRecord> findAll(@Param("table") String table, @Param("limit") int limit);

    @Select("""
            SELECT id, batch_id, name, quantity, amount, status, remark, created_at, updated_at
            FROM ${table}
            WHERE batch_id = #{batchId}
            ORDER BY created_at, id
            LIMIT #{limit}
            """)
    List<DemoRecord> findByBatchId(@Param("table") String table, @Param("batchId") String batchId,
                                   @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM ${table}")
    long count(@Param("table") String table);

    @Insert({
            "<script>",
            "INSERT INTO ${table} (id, batch_id, name, quantity, amount, status, remark, created_at, updated_at) VALUES",
            "<foreach collection='records' item='item' separator=','>",
            "(#{item.id}, #{item.batchId}, #{item.name}, #{item.quantity}, #{item.amount}, #{item.status},",
            " #{item.remark}, #{item.createdAt}, #{item.updatedAt})",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("table") String table, @Param("records") List<DemoRecord> records);

    @Update("""
            UPDATE ${table}
            SET name = name || #{suffix}, status = 'UPDATED', remark = 'updated by demo',
                updated_at = CURRENT_TIMESTAMP
            WHERE id IN (
              SELECT id FROM ${table}
              WHERE batch_id = #{batchId}
              ORDER BY created_at, id
              LIMIT #{limit}
            )
            """)
    int updateFirstN(@Param("table") String table, @Param("batchId") String batchId,
                     @Param("limit") int limit, @Param("suffix") String suffix);

    @Delete("""
            DELETE FROM ${table}
            WHERE id IN (
              SELECT id FROM ${table}
              WHERE batch_id = #{batchId}
              ORDER BY created_at, id
              LIMIT #{limit}
            )
            """)
    int deleteFirstN(@Param("table") String table, @Param("batchId") String batchId,
                     @Param("limit") int limit);
}
