package com.dietbuilder.service;

import com.dietbuilder.model.CulturalFoodGroup;
import com.dietbuilder.model.FoodItem;
import com.dietbuilder.repository.CulturalFoodGroupRepository;
import com.dietbuilder.repository.FoodItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Builds categorized food pools from Mongo {@link FoodItem} rows and cultural groups.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealBankService {

    private final FoodItemRepository foodItemRepository;
    private final CulturalFoodGroupRepository culturalFoodGroupRepository;

    @Value("${openai.plan.hybrid.meal-bank-cache-ttl-minutes:60}")
    private long mealBankCacheTtlMinutes;

    private final Map<String, CachedEntry> cache = new ConcurrentHashMap<>();

    public record MealPools(
            List<FoodItem> proteins,
            List<FoodItem> grains,
            List<FoodItem> vegetables,
            List<FoodItem> fruits,
            List<FoodItem> dairy,
            List<FoodItem> nutsAndOils,
            List<FoodItem> legumes,
            List<FoodItem> fallback
    ) {}

    private record CachedEntry(MealPools pools, Instant createdAt) {
        boolean expired(long ttlMinutes) {
            return Instant.now().isAfter(createdAt.plusSeconds(ttlMinutes * 60));
        }
    }

    public MealPools getOrBuildPools(List<String> cuisines, List<String> dislikedFoods, List<String> ragKeywords) {
        String key = cacheKey(cuisines, dislikedFoods);
        CachedEntry ce = cache.get(key);
        if (ce != null && !ce.expired(mealBankCacheTtlMinutes)) {
            return rankByRagKeywords(ce.pools(), ragKeywords);
        }
        MealPools built = buildPoolsUncached(cuisines, dislikedFoods);
        cache.put(key, new CachedEntry(built, Instant.now()));
        return rankByRagKeywords(built, ragKeywords);
    }

    private static String cacheKey(List<String> cuisines, List<String> dislikes) {
        String c = cuisines == null ? "" : String.join(",", cuisines);
        String d = dislikes == null ? "" : String.join("|", dislikes);
        return c + "::" + d.hashCode();
    }

    private MealPools buildPoolsUncached(List<String> cuisines, List<String> dislikedFoods) {
        Set<Integer> seen = new LinkedHashSet<>();
        List<FoodItem> collected = new ArrayList<>();

        List<String> normalized = cuisines == null ? List.of() : cuisines.stream()
                .filter(x -> x != null && !x.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        if (normalized.isEmpty()) {
            addAllUnique(seen, collected, foodItemRepository.findByCulturalTagsContaining("general"));
        } else {
            for (String culture : normalized) {
                addAllUnique(seen, collected, foodItemRepository.findByCulturalTagsContaining(culture));
            }
            addAllUnique(seen, collected, foodItemRepository.findByCulturalTagsContaining("general"));
        }

        for (CulturalFoodGroup g : loadGroups(normalized)) {
            if (g.getFoods() == null) continue;
            for (CulturalFoodGroup.FoodEquivalent eq : g.getFoods()) {
                if (eq.getFdcId() <= 0) continue;
                foodItemRepository.findByFdcId(eq.getFdcId()).ifPresent(f -> addUnique(seen, collected, f));
            }
        }

        List<FoodItem> filtered = collected.stream()
                .filter(f -> !isDisliked(f, dislikedFoods))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            log.warn("Meal bank empty after filters; falling back to all general-tagged foods");
            addAllUnique(seen, collected, foodItemRepository.findByCulturalTagsContaining("general"));
            filtered = collected.stream()
                    .filter(f -> !isDisliked(f, dislikedFoods))
                    .collect(Collectors.toList());
        }

        List<FoodItem> proteins = new ArrayList<>();
        List<FoodItem> grains = new ArrayList<>();
        List<FoodItem> vegetables = new ArrayList<>();
        List<FoodItem> fruits = new ArrayList<>();
        List<FoodItem> dairy = new ArrayList<>();
        List<FoodItem> nuts = new ArrayList<>();
        List<FoodItem> legumes = new ArrayList<>();
        List<FoodItem> fallback = new ArrayList<>();

        for (FoodItem f : filtered) {
            String cat = f.getFoodCategory() != null ? f.getFoodCategory().toLowerCase(Locale.ROOT) : "";
            switch (cat) {
                case "poultry", "fish", "beef", "meat", "eggs", "shellfish" -> proteins.add(f);
                case "grains", "prepared dishes" -> grains.add(f);
                case "vegetables", "fermented foods" -> vegetables.add(f);
                case "fruits" -> fruits.add(f);
                case "dairy", "dairy alternatives" -> dairy.add(f);
                case "nuts and seeds", "fats and oils" -> nuts.add(f);
                case "legumes" -> legumes.add(f);
                default -> fallback.add(f);
            }
        }

        for (FoodItem f : fallback) {
            String desc = f.getDescription() != null ? f.getDescription().toLowerCase(Locale.ROOT) : "";
            if (desc.contains("bean") || desc.contains("lentil") || desc.contains("chickpea")) {
                legumes.add(f);
            } else if (desc.contains("rice") || desc.contains("bread") || desc.contains("pasta") || desc.contains("oat")) {
                grains.add(f);
            } else if (FoodNutrientHelper.energyPer100g(f) >= 200 && (desc.contains("oil") || desc.contains("butter"))) {
                nuts.add(f);
            } else {
                proteins.add(f);
            }
        }

        MealPools pools = new MealPools(
                ensureNonEmpty(proteins, "protein"),
                ensureNonEmpty(grains, "grain"),
                ensureNonEmpty(vegetables, "vegetable"),
                ensureNonEmpty(fruits, "fruit"),
                dairy.isEmpty() ? List.of() : dairy,
                nuts.isEmpty() ? List.of() : nuts,
                legumes.isEmpty() ? List.of() : legumes,
                List.copyOf(filtered)
        );
        log.info("Meal bank: proteins={} grains={} veg={} fruit={} dairy={} nuts={} legumes={} total={}",
                pools.proteins().size(), pools.grains().size(), pools.vegetables().size(),
                pools.fruits().size(), pools.dairy().size(), pools.nutsAndOils().size(),
                pools.legumes().size(), filtered.size());
        return pools;
    }

    private List<CulturalFoodGroup> loadGroups(List<String> cuisines) {
        if (cuisines.isEmpty()) return List.of();
        List<CulturalFoodGroup> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String c : cuisines) {
            for (CulturalFoodGroup g : culturalFoodGroupRepository.findByCulture(c)) {
                if (g.getId() != null && seen.add(g.getId())) {
                    out.add(g);
                } else if (g.getId() == null) {
                    out.add(g);
                }
            }
        }
        return out;
    }

    private static void addAllUnique(Set<Integer> seen, List<FoodItem> out, List<FoodItem> batch) {
        for (FoodItem f : batch) {
            addUnique(seen, out, f);
        }
    }

    private static void addUnique(Set<Integer> seen, List<FoodItem> out, FoodItem f) {
        if (seen.add(f.getFdcId())) {
            out.add(f);
        }
    }

    private static boolean isDisliked(FoodItem f, List<String> dislikedFoods) {
        if (dislikedFoods == null || dislikedFoods.isEmpty()) return false;
        String desc = f.getDescription() != null ? f.getDescription().toLowerCase(Locale.ROOT) : "";
        for (String d : dislikedFoods) {
            if (d == null || d.isBlank()) continue;
            if (desc.contains(d.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static List<FoodItem> ensureNonEmpty(List<FoodItem> pool, String label) {
        if (!pool.isEmpty()) return pool;
        log.warn("Pool '{}' empty — callers should use fallback list", label);
        return pool;
    }

    /**
     * Light re-ordering: foods whose descriptions match RAG keywords rank earlier.
     */
    MealPools rankByRagKeywords(MealPools pools, List<String> ragKeywords) {
        if (ragKeywords == null || ragKeywords.isEmpty()) {
            return pools;
        }
        List<String> kws = ragKeywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> k.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (kws.isEmpty()) return pools;

        return new MealPools(
                sortByKeywordHits(pools.proteins(), kws),
                sortByKeywordHits(pools.grains(), kws),
                sortByKeywordHits(pools.vegetables(), kws),
                sortByKeywordHits(pools.fruits(), kws),
                sortByKeywordHits(pools.dairy(), kws),
                sortByKeywordHits(pools.nutsAndOils(), kws),
                sortByKeywordHits(pools.legumes(), kws),
                sortByKeywordHits(pools.fallback(), kws)
        );
    }

    private static List<FoodItem> sortByKeywordHits(List<FoodItem> items, List<String> kws) {
        if (items == null || items.isEmpty()) return items == null ? List.of() : items;
        return items.stream()
                .sorted(Comparator.comparingInt((FoodItem f) -> -keywordHits(f, kws)))
                .collect(Collectors.toList());
    }

    private static int keywordHits(FoodItem f, List<String> kws) {
        String desc = (f.getDescription() != null ? f.getDescription() : "").toLowerCase(Locale.ROOT);
        int n = 0;
        for (String k : kws) {
            if (desc.contains(k)) n++;
        }
        return n;
    }

    /** Extract simple tokens from expert retrieval for assembly weights. */
    public static List<String> keywordsFromRetrieval(ExpertKnowledgeService.RetrievalResult retrieval) {
        List<String> out = new ArrayList<>();
        if (retrieval == null) return out;
        if (retrieval.retrievalQuery() != null) {
            for (String t : retrieval.retrievalQuery().toLowerCase(Locale.ROOT).split("\\W+")) {
                if (t.length() >= 4) out.add(t);
            }
        }
        for (var s : retrieval.sources()) {
            if (s.getTitle() != null) {
                for (String t : s.getTitle().toLowerCase(Locale.ROOT).split("\\W+")) {
                    if (t.length() >= 4) out.add(t);
                }
            }
            if (s.getSummary() != null && s.getSummary().length() > 10) {
                String sum = s.getSummary().substring(0, Math.min(200, s.getSummary().length()));
                for (String t : sum.toLowerCase(Locale.ROOT).split("\\W+")) {
                    if (t.length() >= 5) out.add(t);
                }
            }
        }
        return out.stream().distinct().limit(40).collect(Collectors.toList());
    }
}
