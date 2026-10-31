package com.smartspacecleaner.core.sort

/** Какие метки ML Kit к какой категории относятся. Названия — из списка меток базовой модели ML Kit. */
object LabelTable {
    private val table: Map<String, SortCategory> = buildMap {
        fun put(category: SortCategory, vararg names: String) = names.forEach { put(it.lowercase(), category) }
        put(SortCategory.FOOD, "Food", "Dessert", "Cake", "Pizza", "Fast food", "Cuisine", "Bread", "Fruit")
        put(SortCategory.NATURE, "Sky", "Plant", "Flower", "Tree", "Mountain", "Beach", "Sea", "Lake", "River", "Sunset", "Forest")
        put(SortCategory.ANIMALS, "Dog", "Cat", "Bird", "Pet", "Horse", "Fish", "Insect", "Butterfly")
    }

    fun categoryOf(label: String): SortCategory? = table[label.lowercase()]

    /** Самая уверенная метка каждой категории, по убыванию уверенности. */
    fun best(labels: List<Label>): List<Pair<SortCategory, Label>> =
        labels.mapNotNull { label -> categoryOf(label.name)?.let { it to label } }
            .groupBy({ it.first }, { it.second })
            .map { (category, ls) -> category to ls.maxBy { it.confidence } }
            .sortedByDescending { it.second.confidence }
}
