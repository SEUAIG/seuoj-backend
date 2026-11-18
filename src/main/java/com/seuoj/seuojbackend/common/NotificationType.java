package com.seuoj.seuojbackend.common;

/** 站内通知的种类；前端按种类拼文案和跳转链接 */
public enum NotificationType {
    /** 比赛发了新公告（给报名的人），ref = 公告ID */
    CONTEST_ANNOUNCEMENT,
    /** 比赛公告的标题或正文改了（给报名的人），ref = 公告ID */
    CONTEST_ANNOUNCEMENT_UPDATED,
    /** 你的提问有了回复，ref = 答疑ID */
    CLARIFICATION_ANSWERED,
    /** 你的提问的回复改了，ref = 答疑ID */
    CLARIFICATION_ANSWER_UPDATED,
    /** 别人的提问被回复并公开（给报名的人），ref = 答疑ID */
    CLARIFICATION_PUBLIC,
    /** 选手提了新问题（给这场比赛的单独授权者和全站管理员），ref = 答疑ID */
    CLARIFICATION_ASKED
}
