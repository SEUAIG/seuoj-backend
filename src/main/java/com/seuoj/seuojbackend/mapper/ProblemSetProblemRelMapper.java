package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.ProblemSetProblemRel;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProblemSetProblemRelMapper extends BaseMapper<ProblemSetProblemRel> {

    List<ProblemSetProblemRel> selectByProblemSetId(@Param("problemSetId") Long problemSetId);

    int markDeletedByIds(@Param("problemSetId") Long problemSetId, @Param("ids") List<Long> ids);

    int restoreByIdsWithSortOrders(@Param("problemSetId") Long problemSetId,
                                   @Param("rels") List<ProblemSetProblemRel> rels);

    int updateSortOrdersByIds(@Param("problemSetId") Long problemSetId,
                              @Param("rels") List<ProblemSetProblemRel> rels);

    int insertBatch(@Param("rels") List<ProblemSetProblemRel> rels);
}
