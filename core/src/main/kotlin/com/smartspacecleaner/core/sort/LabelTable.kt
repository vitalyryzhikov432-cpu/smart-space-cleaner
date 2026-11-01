package com.smartspacecleaner.core.sort

/** Какие метки ML Kit к какой категории относятся. Названия — из списка меток базовой модели ML Kit. */
object LabelTable {
    private val table: Map<String, SortCategory> = buildMap {
        fun put(category: SortCategory, vararg names: String) = names.forEach { put(it.lowercase(), category) }
        put(SortCategory.DOCUMENTS, "Paper")
        // Человек спиной или вдали: лица не видно, но модель узнаёт позу, одежду и отдых.
        put(SortCategory.PEOPLE, "Person", "Selfie", "Swimwear", "Shorts", "Sitting", "Fun")
        put(SortCategory.FOOD, "Food", "Cake", "Pizza", "Fast food", "Cuisine", "Bread", "Fruit")
        put(SortCategory.NATURE, "Sky", "Plant", "Flower", "Mountain", "Beach", "Lake", "River", "Sunset", "Forest")
        put(SortCategory.ANIMALS, "Dog", "Cat", "Bird", "Pet", "Horse", "Insect", "Butterfly")
    }

    fun categoryOf(label: String): SortCategory? = table[label.lowercase()]

    /** Самая уверенная метка каждой категории, по убыванию уверенности. */
    fun best(labels: List<Label>): List<Pair<SortCategory, Label>> =
        labels.mapNotNull { label -> categoryOf(label.name)?.let { it to label } }
            .groupBy({ it.first }, { it.second })
            .map { (category, ls) -> category to ls.maxBy { it.confidence } }
            .sortedByDescending { it.second.confidence }
}
