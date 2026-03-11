package com.zhicore.content.infrastructure.service;

import com.zhicore.content.domain.service.TagDomainService;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 标签领域服务实现。
 *
 * 仅负责标签名称规则和 slug 规范化，不承担标签持久化与 ID 生成编排。
 */
@Slf4j
@Service
public class TagDomainServiceImpl implements TagDomainService {

    /** 连续空白字符 */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** 连续连字符 */
    private static final Pattern MULTI_DASH = Pattern.compile("-+");
    /** 首尾连字符 */
    private static final Pattern LEADING_TRAILING_DASH = Pattern.compile("^-+|-+$");
    /** 非法 slug 字符（仅保留 a-z0-9-） */
    private static final Pattern ILLEGAL_SLUG_CHARS = Pattern.compile("[^a-z0-9-]");
    /** 合法标签名称字符 */
    private static final Pattern VALID_TAG_NAME = Pattern.compile("^[\\u4e00-\\u9fa5a-zA-Z0-9\\s\\-_]+$");
    /** 中文字符 */
    private static final Pattern CHINESE_CHAR = Pattern.compile("[\\u4e00-\\u9fa5]");

    @Override
    public String normalizeToSlug(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("标签名称不能为空");
        }

        String result = name.trim();
        result = convertChineseToPinyin(result);
        result = result.toLowerCase();
        result = WHITESPACE.matcher(result).replaceAll("-");
        result = MULTI_DASH.matcher(result).replaceAll("-");
        result = LEADING_TRAILING_DASH.matcher(result).replaceAll("");
        result = ILLEGAL_SLUG_CHARS.matcher(result).replaceAll("");

        if (!StringUtils.hasText(result)) {
            throw new IllegalArgumentException("标签名称规范化后为空，无法创建标签");
        }
        return result;
    }

    @Override
    public void validateTagName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("标签名称不能为空");
        }

        String trimmedName = name.trim();
        if (trimmedName.length() > 50) {
            throw new IllegalArgumentException("标签名称不能超过50字符");
        }
        if (!VALID_TAG_NAME.matcher(trimmedName).matches()) {
            throw new IllegalArgumentException("标签名称只能包含中文、英文、数字、空格、连字符和下划线");
        }
    }

    private String convertChineseToPinyin(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);

        StringBuilder result = new StringBuilder();
        char[] chars = text.toCharArray();
        for (char c : chars) {
            if (CHINESE_CHAR.matcher(Character.toString(c)).matches()) {
                try {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        result.append(pinyinArray[0]);
                    } else {
                        result.append(c);
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    log.warn("Failed to convert Chinese character to pinyin: {}", c, e);
                    result.append(c);
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
