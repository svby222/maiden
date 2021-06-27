package bot.maiden.common

import java.math.BigDecimal
import java.math.BigInteger
import java.util.*
import kotlin.reflect.KClass

interface ArgumentConverter<in From : Any, out To : Any> {
    val fromType: KClass<in From>
    val toType: KClass<out To>

    suspend fun convert(from: From): Result<To>
}

class ConversionSet {
    internal data class ConverterData(
        val converter: ArgumentConverter<*, *>,
        val priority: Int,
    )

    internal val converters = hashMapOf<KClass<*>, MutableMap<KClass<*>, ConverterData>>()

    // Note: only one converter for each type pair
    fun <From : Any, To : Any> addConverter(
        fromType: KClass<From>,
        toType: KClass<To>,
        converter: ArgumentConverter<From, To>,
        priority: Int
    ) {
        converters.getOrPut(fromType) { hashMapOf() }[toType] = ConverterData(converter, priority)
    }

    inline fun <reified From : Any, reified To : Any> addConverter(
        converter: ArgumentConverter<From, To>,
        priority: Int
    ) = addConverter(From::class, To::class, converter, priority)

    fun getConverterList(fromType: KClass<*>, toType: KClass<*>): List<Pair<ArgumentConverter<*, *>, Int>>? {
        // TODO: currently, this performs DFS; another algorithm + caching may be more effective (i.e. Johnson)
        // TODO: cache results?

        val nodeStack = LinkedList<KClass<*>>()
        val visited = hashSetOf<KClass<*>>()
        val previous = hashMapOf<KClass<*>, KClass<*>>()

        nodeStack.push(fromType)

        var success = false

        while (nodeStack.isNotEmpty()) {
            val next = nodeStack.pop()
            if (next == toType) {
                // Done
                success = true
                break
            } else if (next !in visited) {
                visited.add(next)
                converters[next]?.let {
                    it.keys.forEach { key ->
                        previous[key] = next
                        nodeStack.push(key)
                    }
                }
            }
        }

        if (!success) return null

        val resultsStack = LinkedList<KClass<*>>()

        var current: KClass<*>? = toType
        while (current != null) {
            resultsStack.push(current)
            current = previous[current]
        }

        var first = resultsStack.pop()
        var second: KClass<*>? = resultsStack.poll()

        return sequence {
            while (second != null) {
                val data = converters[first]?.get(second)
                    ?: throw IllegalStateException("Conversion from $first to $second discovered by pathfinding, but none was present in the ${ConversionSet::class.simpleName}")
                val converter = data.converter

                yield(Pair(converter, data.priority))

                first = second
                second = resultsStack.poll()
            }
        }.toList()
    }
}

// Sorted by size in ascending order
private val FIXED_TYPES = listOf<Pair<KClass<*>, (Number) -> Number>>(
    Byte::class to Number::toByte,
    Short::class to Number::toShort,
    Int::class to Number::toInt,
    Long::class to Number::toLong,
    BigInteger::class to { it.toLong().toBigInteger() }
)

private val FLOATING_TYPES = listOf<Pair<KClass<*>, (Number) -> Number>>(
    Float::class to Number::toFloat,
    Double::class to Number::toDouble,
    BigDecimal::class to { it.toDouble().toBigDecimal() },
)

private val NUMERIC_TYPES = listOf(
    FIXED_TYPES,
    FLOATING_TYPES
)

fun addPrimitiveConverters(set: ConversionSet) {
    for (list in NUMERIC_TYPES) {
        for (i in list.indices) {
            val (fromType) = list[i]

            // Add implicit (widening) conversions
            for ((toType, convertToFunc) in list.drop(i + 1)) {
                println("$fromType -> $toType")

                val converter = object : ArgumentConverter<Any, Any> {
                    @Suppress("UNCHECKED_CAST")
                    override val fromType
                        get() = fromType as KClass<Any>
                    override val toType get() = toType

                    private val convertToFunc = convertToFunc

                    override suspend fun convert(from: Any): Result<Any> {
                        return Result.success(this.convertToFunc(from as Number))
                    }
                }

                @Suppress("UNCHECKED_CAST")
                set.addConverter(fromType as KClass<Any>, toType as KClass<Any>, converter, 0)
            }
        }
    }

    // Add BigInteger -> BigDecimal converter (enables ƒixed -> floating conversion)
    set.addConverter(BigInteger::class, BigDecimal::class, object : ArgumentConverter<BigInteger, BigDecimal> {
        override val fromType get() = BigInteger::class
        override val toType get() = BigDecimal::class
        override suspend fun convert(from: BigInteger) = Result.success(from.toBigDecimal())
    }, 0)
}
