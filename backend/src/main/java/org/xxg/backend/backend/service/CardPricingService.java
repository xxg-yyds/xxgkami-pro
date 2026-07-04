package org.xxg.backend.backend.service;

import org.springframework.stereotype.Service;
import org.xxg.backend.backend.entity.CardPricing;
import org.xxg.backend.backend.mapper.CardPricingMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;

@Service
public class CardPricingService {

    private final CardPricingMapper cardPricingMapper;

    public CardPricingService(CardPricingMapper cardPricingMapper) {
        this.cardPricingMapper = cardPricingMapper;
    }

    public Map<String, List<CardPricing>> getAllPricing() {
        List<CardPricing> all = cardPricingMapper.findAll();
        Map<String, List<CardPricing>> result = new HashMap<>();
        result.put("timeCards", all.stream().filter(p -> "time".equals(p.getType())).collect(Collectors.toList()));
        result.put("countCards", all.stream().filter(p -> "count".equals(p.getType())).collect(Collectors.toList()));
        return result;
    }

    public void addPricing(CardPricing pricing) {
        validatePricing(pricing, null);
        cardPricingMapper.insert(pricing);
    }

    public void updatePricing(CardPricing pricing) {
        if (pricing.getId() == null) {
            throw new RuntimeException("缺少定价 ID");
        }
        if (cardPricingMapper.findById(pricing.getId()) == null) {
            throw new RuntimeException("定价不存在");
        }
        validatePricing(pricing, pricing.getId());
        cardPricingMapper.update(pricing);
    }

    public void deletePricing(Integer id) {
        cardPricingMapper.delete(id);
    }

    private void validatePricing(CardPricing pricing, Integer excludeId) {
        if (pricing == null) {
            throw new RuntimeException("请求数据不能为空");
        }
        String type = pricing.getType();
        if (type == null || (!"time".equals(type) && !"count".equals(type))) {
            throw new RuntimeException("类型须为 time 或 count");
        }
        Integer value = pricing.getValue();
        if (value == null || value < 1) {
            throw new RuntimeException("time".equals(type) ? "时长须为大于 0 的整数天" : "次数须为大于 0 的整数");
        }
        BigDecimal price = pricing.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("价格须大于 0");
        }
        String description = pricing.getDescription();
        if (description == null || description.trim().isEmpty()) {
            throw new RuntimeException("描述不能为空");
        }
        if (description.trim().length() > 100) {
            throw new RuntimeException("描述不能超过 100 个字符");
        }
        pricing.setDescription(description.trim());
        if (cardPricingMapper.existsByTypeAndValue(type, value, excludeId)) {
            throw new RuntimeException("time".equals(type)
                    ? "已存在相同天数的时间卡定价"
                    : "已存在相同次数的次数卡定价");
        }
    }
}
