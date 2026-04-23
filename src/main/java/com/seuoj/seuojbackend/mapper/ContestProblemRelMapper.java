package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.ContestProblemRel;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ContestProblemRelMapper extends BaseMapper<ContestProblemRel> {

    List<ContestProblemRel> selectByContestId(@Param("contestId") Long contestId);

    int markDeletedByIds(@Param("contestId") Long contestId, @Param("ids") List<Long> ids);

    int restoreByIdsWithSortOrders(@Param("contestId") Long contestId,
                                   @Param("rels") List<ContestProblemRel> rels);

    int updateSortOrdersByIds(@Param("contestId") Long contestId,
                              @Param("rels") List<ContestProblemRel> rels);

    int insertBatch(@Param("rels") List<ContestProblemRel> rels);
}
