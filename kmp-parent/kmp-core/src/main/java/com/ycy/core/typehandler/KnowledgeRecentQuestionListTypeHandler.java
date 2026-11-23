package com.ycy.core.typehandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ycy.core.dto.KnowledgeRecentQuestion;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * {@code kmp_knowledge_question_cluster.recent_questions} JSON 字段与
 * {@code List<KnowledgeRecentQuestion>} 之间的 MyBatis 类型处理器。
 *
 * <p>该字段直接以 JSON 数组落库，读取时反序列化为对象列表，写入时为 null 则写 {@code null}。</p>
 */
@MappedTypes(List.class)
public class KnowledgeRecentQuestionListTypeHandler
        extends BaseTypeHandler<List<KnowledgeRecentQuestion>> {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final TypeReference<List<KnowledgeRecentQuestion>> TYPE_REF =
            new TypeReference<List<KnowledgeRecentQuestion>>() {
            };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<KnowledgeRecentQuestion> parameter,
                                    JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, MAPPER.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException("序列化 recentQuestions 失败", e);
        }
    }

    @Override
    public List<KnowledgeRecentQuestion> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<KnowledgeRecentQuestion> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<KnowledgeRecentQuestion> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private List<KnowledgeRecentQuestion> parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, TYPE_REF);
        } catch (Exception e) {
            throw new SQLException("反序列化 recentQuestions 失败", e);
        }
    }
}
