package com.seuoj.seuojbackend.common;

/** 发言挂在哪里，对应 comment.target_type */
public enum CommentTargetType {
    /** 题目讨论，target_id = problem.id */
    PROBLEM,
    /** 整场比赛讨论，target_id = contest.id */
    CONTEST,
    /** 帖子评论区，target_id = post.id（1b-2 才开放） */
    POST
}
