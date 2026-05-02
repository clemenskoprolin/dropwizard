package j2k.evaluator

data class ConversionResult(
    val variant: String,
    val javaFileCount: Int,
    val convertedCount: Int,
    val failedCount: Int,
    val skipped: Boolean = false,
    val skipReason: String = ""
)

data class StructuralMetrics(
    val classCount: Int,
    val interfaceCount: Int,
    val enumCount: Int,
    val annotationCount: Int,
    val publicMethodCount: Int
)

data class KotlinHeuristicMetrics(
    val parseSuccessCount: Int,
    val parseFailureCount: Int,
    val unsafeCallCount: Int,
    val unsafeCastCount: Int,
    val todoCommentCount: Int,
    val dataClassCount: Int,
    val objectCount: Int
)

data class PrimaryEvaluationResult(
    val variant: String,
    val javaLoc: Int,
    val kotlinLoc: Int,
    val conversion: ConversionResult,
    val javaStructural: StructuralMetrics,
    val kotlinStructural: StructuralMetrics,
    val kotlinHeuristics: KotlinHeuristicMetrics,
    val hotspots: List<HotspotFile>
)

data class HotspotFile(
    val path: String,
    val unsafeCalls: Int,
    val unsafeCasts: Int,
    val todoComments: Int
)

data class PetclinicComparisonResult(
    val variant: String,
    val convertedClassCount: Int,
    val officialClassCount: Int,
    val matchedClassCount: Int,
    val convertedPackageCount: Int,
    val officialPackageCount: Int,
    val convertedAnnotationCount: Int,
    val officialAnnotationCount: Int,
    val classNameMatchPct: Double,
    val packageMatchPct: Double,
    val annotationParityPct: Double
)

data class EdgeCaseHypothesis(
    val id: String,
    val file: String,
    val category: String,
    val hypothesis: String,
    val expectedIssues: List<String>
)

data class EdgeCaseResult(
    val id: String,
    val file: String,
    val hypothesis: String,
    val passed: Boolean,
    val failureNote: String,
    val unsafeCallCount: Int,
    val unsafeCastCount: Int,
    val conversionSucceeded: Boolean
)
