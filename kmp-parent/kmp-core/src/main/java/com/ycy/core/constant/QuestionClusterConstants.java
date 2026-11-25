package com.ycy.core.constant;

/**
 * 问题簇状态及字段长度常量。
 */
public final class QuestionClusterConstants
{
    /** 沉淀中。 */
    public static final int STATUS_ACCUMULATING = 0;

    /** 待发布。 */
    public static final int STATUS_PENDING = 1;

    /** 已发布。 */
    public static final int STATUS_PUBLISHED = 2;

    /** 标准问题最大长度。 */
    public static final int MAX_QUESTION_LENGTH = 200;


    /** 标准答案最大长度。 */
    public static final int MAX_ANSWER_LENGTH = 5000;
    /** FAQ推荐最大长度。 */
    public static final int FAQ_RECOMMENDATION_LIMIT = 10;
    /** FAQ建议最小长度。 仅识别大于等于该长度字符 */
    public static final int FAQ_SUGGESTION_MIN_LENGTH = 5;
    /** FAQ结果条数 对应es中的size */
    public static final int FAQ_SUGGESTION_LIMIT = 10;
    /**FAQ建议最小分数。仅识别大于等于该分数结果  对应es中的min_score */
    public static final double FAQ_SUGGESTION_MIN_SCORE = 0.5;

}
