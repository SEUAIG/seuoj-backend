package com.seuoj.seuojbackend.vo.me;

import lombok.Data;

import java.util.List;

@Data
public class MeHeatmapVO {
    private String year;
    private List<HeatmapDayVO> days;
    private HeatmapSummaryVO summary;
}
