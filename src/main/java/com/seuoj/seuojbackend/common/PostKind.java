package com.seuoj.seuojbackend.common;

/** 帖子类型，对应 post.kind */
public enum PostKind {
    /** 比赛官方题解，每场最多一篇 */
    EDITORIAL,
    /** 用户题解，挂在题目上 */
    SOLUTION,
    /** 博客 */
    BLOG
}
