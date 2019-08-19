/*
 * Copyright 2019 Oliver Berg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ================== GUAVA LICENSE ==================
 *
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package moe.kanon.konfig.internal

import moe.kanon.kommons.UNSUPPORTED
import moe.kanon.kommons.collections.createArray
import moe.kanon.kommons.collections.mapToTypedArray
import moe.kanon.kommons.func.tuples.toT
import moe.kanon.kommons.requireThat
import java.io.Serializable
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.GenericArrayType
import java.lang.reflect.GenericDeclaration
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.security.AccessControlException
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.Array
import kotlin.reflect.KClass
import java.lang.reflect.Array as ArrayType


/*
 * This file contains ports of classes that can be found in the reflection[1] package of the Guava library for Java.
 *
 * [1]: https://github.com/google/guava/tree/master/guava/src/com/google/common/reflect
 */

/*
 * This file is here because I don't want to include the entirety of the Guava library just to use the TypeToken
 * utility, as Guava is quite a hefty dependency, and this file can be completely removed *(most likely)* in Kotlin 1.4
 * as the 'typeOf' function should support conversion of the KType class to a Java Type properly at that point, which
 * means rather than having to use reflection and local classes to capture the generics, we can simply just use
 * 'typeOf<T>()' on a reified generic parameter, which would produce less overhead and be a lot cleaner, until then,
 * we're using TypeTokens, internally.
 */

abstract class TypeCapture<T> protected constructor() {
    fun capture(): Type {
        val type = javaClass.genericSuperclass
        requireThat(type is ParameterizedType) { "$type isn't parameterized" }
        return type.actualTypeArguments[0]
    }
}

abstract class TypeParameter<T> protected constructor() : TypeCapture<T>() {
    internal val typeVariable: TypeVariable<*>

    init {
        val type = capture()
        requireThat(type is TypeVariable<*>) { "$type needs to be a type-variable" }
        typeVariable = type
    }

    override fun equals(other: Any?): Boolean = when (other) {
        is TypeParameter<*> -> typeVariable == other.typeVariable
        else -> false
    }

    override fun hashCode(): Int = typeVariable.hashCode()

    override fun toString(): String = typeVariable.toString()
}

@Suppress("UNCHECKED_CAST")
abstract class TypeToken<T> : TypeCapture<T>, Serializable {
    companion object {
        fun of(type: Type): TypeToken<*> = object : TypeToken<Any>(type) {}
    }

    protected val runtimeType: Type

    /**
     * Resolver for resolving parameter and field types with [.runtimeType] as context.
     */
    @Transient
    private var _invariantTypeResolver: TypeResolver? = null

    /**
     * Resolver for resolving covariant types with [.runtimeType] as context.
     */
    @Transient
    private var _covariantTypeResolver: TypeResolver? = null

    protected constructor() : super() {
        runtimeType = capture()
        requireThat(runtimeType !is TypeVariable<*>) {
            """
            |Cannot construct a TypeToken for a type variable.
            |You probably meant to call "TypeToken<$runtimeType>(getClass())" that can resolve the type variable for you.
            |If you do need to create a TypeToken of a type variable, please use TypeToken.of() instead.
            """.trimMargin()
        }
    }

    /**
     * Constructs a new type token of `T` while resolving free type variables in the context of
     * `declaringClass`.
     *
     *
     * Clients create an empty anonymous subclass. Doing so embeds the type parameter in the
     * anonymous class's type hierarchy so we can reconstitute it at runtime despite erasure.
     *
     *
     * For example:
     *
     * ```kotlin
     *  abstract class IKnowMyType<T> {
     *      TypeToken<T> getMyType() {
     *          return new TypeToken<T>(getClass()) {};
     *      }
     *  }
     *
     *  IKnowMyType<String>() {}.getMyType() => String
     * ```
     */
    protected constructor(declaringClass: Class<*>) : super() {
        val captured = super.capture()
        runtimeType =
            if (captured is Class<*>) captured else TypeResolver.covariantly(declaringClass).resolveType(captured)
    }

    private constructor(type: Type) : super() {
        this.runtimeType = type
    }

    /**
     * Returns an instance of type token that wraps `type`.
     */
    infix fun <T> of(type: Class<T>): TypeToken<T> = SimpleTypeToken(type)

    /**
     * Returns an instance of type token that wraps `type`.
     */
    infix fun of(type: Type): TypeToken<*> = SimpleTypeToken<Any>(type)

    /**
     * Returns the raw type of `T`. Formally speaking, if `T` is returned by
     * [Method.getGenericReturnType][java.lang.reflect.Method.getGenericReturnType], the raw type is what's returned by
     * [Method.getReturnType][java.lang.reflect.Method.getReturnType] of the same method object. Specifically:
     * - If `T` is a `Class` itself, `T` itself is returned.
     * - If `T` is a [ParameterizedType], the raw type of the parameterized type is returned.
     * - If `T` is a [GenericArrayType], the returned type is the corresponding array class. For example:
     *  `List<Integer>[] => List[]`.
     * - If `T` is a type variable or a wildcard type, the raw type of the first upper bound is returned. For example:
     *  `<X extends Foo> => Foo`.
     */
    // For wildcard or type variable, the first bound determines the runtime type.
    val rawType: Class<in T>
        get() = rawTypes.iterator().next()

    /**
     * Returns the represented type.
     */
    val type: Type
        get() = runtimeType

    /**
     * Returns a new `TypeToken` where type variables represented by `typeParam` are
     * substituted by `typeArg`. For example, it can be used to construct `Map<K, V>` for
     * any `K` and `V` type:
     *
     * <pre>`static <K, V> TypeToken<Map<K, V>> mapOf(
     * TypeToken<K> keyType, TypeToken<V> valueType) {
     * return new TypeToken<Map<K, V>>() {}
     * .where(new TypeParameter<K>() {}, keyType)
     * .where(new TypeParameter<V>() {}, valueType);
     * }
    `</pre> *
     *
     * @param <X> The parameter type
     * @param typeParam the parameter type variable
     * @param typeArg the actual type to substitute
    </X> */
    fun <X> where(typeParam: TypeParameter<X>, typeArg: TypeToken<X>): TypeToken<T> {
        val resolver = TypeResolver() where mapOf(
            TypeResolver.TypeVariableKey(typeParam.typeVariable) to typeArg.runtimeType
        )
        // If there's any type error, we'd report now rather than later.
        return SimpleTypeToken(resolver.resolveType(runtimeType))
    }

    /**
     * Returns a new `TypeToken` where type variables represented by `typeParam` are
     * substituted by `typeArg`. For example, it can be used to construct `Map<K, V>` for
     * any `K` and `V` type:
     *
     * <pre>`static <K, V> TypeToken<Map<K, V>> mapOf(
     * Class<K> keyType, Class<V> valueType) {
     * return new TypeToken<Map<K, V>>() {}
     * .where(new TypeParameter<K>() {}, keyType)
     * .where(new TypeParameter<V>() {}, valueType);
     * }
    `</pre> *
     *
     * @param <X> The parameter type
     * @param typeParam the parameter type variable
     * @param typeArg the actual type to substitute
    </X> */
    fun <X> where(typeParam: TypeParameter<X>, typeArg: Class<X>): TypeToken<T> = where(typeParam, of(typeArg))

    /**
     * Resolves the given `type` against the type context represented by this type. For example:
     *
     * <pre>`new TypeToken<List<String>>() {}.resolveType(
     * List.class.getMethod("get", int.class).getGenericReturnType())
     * => String.class
    `</pre> *
     */
    // Being conservative here because the user could use resolveType() to resolve a type in an
    // invariant context.
    fun resolveType(type: Type): TypeToken<*> = of(invariantTypeResolver!!.resolveType(type))

    private fun Type.resolveSupertype(): TypeToken<*> {
        val supertype = of(covariantTypeResolver!!.resolveType(this))
        // super types' type mapping is a subset of type mapping of this type.
        supertype._covariantTypeResolver = _covariantTypeResolver
        supertype._invariantTypeResolver = _invariantTypeResolver
        return supertype
    }

    /**
     * Returns the generic superclass of this type or `null` if the type represents [ ] or an interface.
     *
     * This method is similar but different from [ ][Class.getGenericSuperclass]. For example, `new TypeToken<StringArrayList>()
     * {}.getGenericSuperclass()` will return `new TypeToken<ArrayList<String>>() {}`; while
     * `StringArrayList.class.getGenericSuperclass()` will return `ArrayList<E>`, where
     * `E` is the type variable declared by class `ArrayList`.
     *
     *
     * If this type is a type variable or wildcard, its first upper bound is examined and returned
     * if the bound is a class or extends from a class. This means that the returned type could be a
     * type variable too.
     */
    // First bound is always the super class, if one exists.
    @Suppress("UNCHECKED_CAST")
    val genericSuperclass: TypeToken<in T>?
        get() = when (runtimeType) {
            is TypeVariable<*> -> runtimeType.bounds[0].boundAsSuperclass()
            // wildcard has one and only one upper bound.
            is WildcardType -> runtimeType.upperBounds[0].boundAsSuperclass()
            else -> {
                val superclass = rawType.genericSuperclass ?: null
                superclass?.resolveSupertype() as TypeToken<in T>?
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun Type.boundAsSuperclass(): TypeToken<in T>? =
        if (of(this).rawType.isInterface) null else of(this) as TypeToken<in T>

    /**
     * Returns the generic interfaces that this type directly `implements`. This method is
     * similar but different from [Class.getGenericInterfaces]. For example, `new
     * TypeToken<List<String>>() {}.getGenericInterfaces()` will return a list that contains `new
     * TypeToken<Iterable<String>>() {}`; while `List.class.getGenericInterfaces()` will
     * return an array that contains `Iterable<T>`, where the `T` is the type variable
     * declared by interface `Iterable`.
     *
     *
     * If this type is a type variable or wildcard, its upper bounds are examined and those that
     * are either an interface or upper-bounded only by interfaces are returned. This means that the
     * returned types could include type variables too.
     */
    @Suppress("UNCHECKED_CAST")
    val genericInterfaces: List<TypeToken<in T>>
        get() = when (runtimeType) {
            is TypeVariable<*> -> runtimeType.bounds.boundsAsInterfaces()
            is WildcardType -> runtimeType.upperBounds.boundsAsInterfaces()
            else -> rawType.genericInterfaces.map { it.resolveSupertype() as TypeToken<in T> }
        }

    @Suppress("UNCHECKED_CAST")
    private fun Array<Type>.boundsAsInterfaces(): List<TypeToken<in T>> =
        map { of(it) as TypeToken<in T> }.filter { it.rawType.isInterface }

    /**
     * Returns the set of interfaces and classes that this type is or is a subtype of. The returned
     * types are parameterized with proper type arguments.
     *
     *
     * Subtypes are always listed before supertypes. But the reverse is not true. A type isn't
     * necessarily a subtype of all the types following. Order between types without subtype
     * relationship is arbitrary and not guaranteed.
     *
     *
     * If this type is a type variable or wildcard, upper bounds that are themselves type variables
     * aren't included (their super interfaces and superclasses are).
     */
    val types: TypeSet
        get() = TypeSet()

    /**
     * Returns the generic form of `superClass`. For example, if this is `ArrayList<String>`, `Iterable<String>` is returned given the input `Iterable.class`.
     */
    @Suppress("UNCHECKED_CAST")
    infix fun asSuperTypeOf(superClass: Class<T>): TypeToken<in T> {
        requireThat(this.someRawTypeIsSubclassOf(superClass)) { "<$superClass> is not a super class of <$this>" }
        return when {
            runtimeType is TypeVariable<*> -> superClass.getSupertypeFromUpperBounds(runtimeType.bounds)
            runtimeType is WildcardType -> superClass.getSupertypeFromUpperBounds(runtimeType.upperBounds)
            superClass.isArray -> superClass.arraySupertype
            else -> toGenericType(superClass).runtimeType.resolveSupertype() as TypeToken<in T>
        }
    }

    /**
     * Returns subtype of `this` with `subClass` as the raw class. For example, if this is
     * `Iterable<String>` and `subClass` is `List`, `List<String>` is
     * returned.
     */
    @Suppress("UNCHECKED_CAST")
    infix fun asSubTypeOf(subClass: Class<*>): TypeToken<out T> {
        requireThat(runtimeType !is TypeVariable<*>) { "Cannot get subtype of type variable <$this>" }
        return when {
            runtimeType is WildcardType -> subClass.getSubtypeFromLowerBounds(runtimeType.lowerBounds)
            // unwrap array type if necessary
            isArray -> subClass.arraySubtype
            // At this point, it's either a raw class or parameterized type.
            else -> {
                requireThat(rawType.isAssignableFrom(subClass)) { "<$subClass> isn't a subClass of <$this>" }
                val resolvedTypeArgs = subClass.resolveTypeArgsForSubclass()
                // guarded by the isAssignableFrom() statement above
                val subtype = of(resolvedTypeArgs) as TypeToken<out T>
                requireThat(subtype.isSubTypeOf(this)) { "<$subtype> does not appear to be a subtype of <$this>" }

                subtype
            }
        }
    }

    /**
     * Returns true if this type is a supertype of the given `type`. "Supertype" is defined
     * according to [the rules for type arguments](http://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-4.5.1) introduced with Java generics.
     */
    infix fun isSuperTypeOf(type: TypeToken<*>): Boolean = type.isSubTypeOf(type)

    /**
     * Returns true if this type is a supertype of the given `type`. "Supertype" is defined
     * according to [the rules for type arguments](http://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-4.5.1) introduced with Java generics.
     */
    infix fun isSuperTypeOf(type: Type): Boolean = of(type).isSubTypeOf(type)

    /**
     * Returns true if this type is a subtype of the given `type`. "Subtype" is defined
     * according to [the rules for type arguments](http://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-4.5.1) introduced with Java generics.
     */
    infix fun isSubTypeOf(type: TypeToken<*>): Boolean = isSubTypeOf(type.type)

    /**
     * Returns true if this type is a subtype of the given `type`. "Subtype" is defined
     * according to [the rules for type arguments](http://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-4.5.1) introduced with Java generics.
     */
    // if 'superType' is <? super Foo>, 'this' can be:
    // Foo, SubFoo, <? extends Foo>.
    // if 'superType' is <? extends Foo>, nothing is a subtype.
    infix fun isSubTypeOf(superType: Type): Boolean = when (superType) {
        is WildcardType -> any(superType.lowerBounds) isSuperTypeOf runtimeType
        is Class<*> -> this someRawTypeIsSubclassOf superType
        is ParameterizedType -> this isSubtypeOfParameterizedType superType
        is GenericArrayType -> this isSubtypeOfArrayType superType
        else -> when (runtimeType) {
            is WildcardType -> any(runtimeType.upperBounds) isSubTypeOf superType
            is TypeVariable<*> -> (runtimeType == superType || any(runtimeType.bounds) isSubTypeOf superType)
            is GenericArrayType -> of(superType) isSupertypeOfArray runtimeType
            else -> false
        }
    }

    /**
     * Returns true if this type is known to be an array type, such as `int[]`, `T[]`,
     * `<? extends Map<String, Integer>[]>` etc.
     */
    val isArray: Boolean
        get() = componentType != null

    /**
     * Returns true if this type is one of the nine primitive types (including `void`).
     *
     * @since 15.0
     */
    val isPrimitive: Boolean
        get() = (runtimeType is Class<*>) && runtimeType.isPrimitive

    /**
     * Returns the array component type if this type represents an array (`int[]`, `T[]`,
     * `<? extends Map<String, Integer>[]>` etc.), or else `null` is returned.
     */
    val componentType: TypeToken<*>?
        get() = runtimeType.componentType?.let { of(it) }

    /**
     * The set of interfaces and classes that `T` is or is a subtype of. [Object] is not included in the set if this type
     * is an interface.
     */
    open inner class TypeSet(
        private val types: Set<TypeToken<in T>> = TypeCollector.FOR_GENERIC_TYPE
            .collectTypes(this@TypeToken)
            .filter(TypeFilter.IGNORE_TYPE_VARIABLE_OR_WILDCARD)
            .toSet() as Set<TypeToken<in T>>
    ) : Set<TypeToken<in T>> by types, Serializable {
        /**
         * Returns the types that are interfaces implemented by this type.
         */
        open val interfaces: TypeSet
            get() = InterfaceSet(this)

        /**
         * Returns the types that are classes.
         */
        open val classes: TypeSet
            get() = ClassSet()

        /**
         * Returns the raw types of the types in this set, in the same order.
         */
        // Java has no way to express ? super T when we parameterize TypeToken vs. Class.
        open val rawTypes: Set<Class<in T>>
            get() = TypeCollector.FOR_RAW_TYPE.collectTypes(rawTypes).toSet() as Set<Class<in T>>
    }

    private inner class InterfaceSet(
        @field:Transient private val allTypes: TypeSet,
        @field:Transient private val _interfaces: Set<TypeToken<in T>> = allTypes.filter(TypeFilter.INTERFACE_ONLY)
            .toSet()
    ) : TypeSet() {
        override val interfaces: TypeSet
            get() = this

        // Java has no way to express ? super T when we parameterize TypeToken vs. Class.
        override val rawTypes: Set<Class<in T>>
            get() = TypeCollector.FOR_RAW_TYPE.collectTypes(rawTypes).filter { it.isInterface }.toSet() as Set<Class<in T>>

        override val classes: Nothing
            get() = throw UnsupportedOperationException("interfaces().classes() not supported.")

        private fun readResolve(): Any = types.interfaces
    }

    private inner class ClassSet(
        private val _classes: Set<TypeToken<in T>> = TypeCollector.FOR_GENERIC_TYPE
            .classesOnly
            .collectTypes(this@TypeToken)
            .filter(TypeFilter.IGNORE_TYPE_VARIABLE_OR_WILDCARD)
            .toSet() as Set<TypeToken<in T>>
    ) : TypeSet() {

        override val classes: TypeSet
            get() = this

        // Java has no way to express ? super T when we parameterize TypeToken vs. Class.
        override val rawTypes: Set<Class<in T>>
            get() = TypeCollector.FOR_RAW_TYPE.classesOnly.collectTypes(rawTypes).toSet() as Set<Class<in T>>

        override val interfaces: Nothing
            get() = throw UnsupportedOperationException("classes().interfaces() not supported.")

        private fun readResolve(): Any = types.classes
    }

    private enum class TypeFilter : (TypeToken<*>) -> Boolean {
        IGNORE_TYPE_VARIABLE_OR_WILDCARD {
            override fun invoke(type: TypeToken<*>): Boolean =
                !((type.runtimeType is TypeVariable<*> || type.runtimeType is WildcardType))
        },
        INTERFACE_ONLY {
            override fun invoke(type: TypeToken<*>): Boolean = type.rawType.isInterface
        }
    }

    /**
     * Returns true if `o` is another `TypeToken` that represents the same [Type].
     */
    override fun equals(other: Any?): Boolean = when (other) {
        is TypeToken<*> -> runtimeType == other.runtimeType
        else -> false
    }

    override fun hashCode(): Int = runtimeType.hashCode()

    override fun toString(): String = runtimeType.prettyString

    /** Implemented to support serialization of subclasses.  */
    protected fun writeReplace(): Any {
        // TypeResolver just transforms the type to our own impls that are Serializable
        // except TypeVariable.
        return of(TypeResolver().resolveType(runtimeType))
    }

    /**
     * Ensures that this type token doesn't contain type variables, which can cause unchecked type
     * errors for callers like [TypeToInstanceMap].
     */
    fun rejectTypeVariables(): TypeToken<T> {
        object : TypeVisitor() {
            override fun visitTypeVariable(type: TypeVariable<*>): Nothing = throw IllegalArgumentException(
                "<${(runtimeType)}> contains a type variable and is not safe for the operation"
            )

            override fun visitWildcardType(type: WildcardType) {
                visit(*type.lowerBounds)
                visit(*type.upperBounds)
            }

            override fun visitParameterizedType(type: ParameterizedType) {
                visit(*type.actualTypeArguments)
                visit(type.ownerType)
            }

            override fun visitGenericArrayType(type: GenericArrayType) {
                visit(type.genericComponentType)
            }
        }.visit(runtimeType)
        return this
    }

    private infix fun someRawTypeIsSubclassOf(superclass: Class<*>): Boolean =
        rawTypes.any { superclass.isAssignableFrom(it) }

    private infix fun isSubtypeOfParameterizedType(supertype: ParameterizedType): Boolean {
        val matchedClass = of(supertype).rawType
        if (!someRawTypeIsSubclassOf(matchedClass)) {
            return false
        }
        val typeVars = matchedClass.typeParameters
        val supertypeArgs = supertype.actualTypeArguments
        for (i in typeVars.indices) {
            val subtypeParam = covariantTypeResolver!!.resolveType(typeVars[i])
            // If 'supertype' is "List<? extends CharSequence>"
            // and 'this' is StringArrayList,
            // First step is to figure out StringArrayList "is-a" List<E> where <E> = String.
            // String is then matched against <? extends CharSequence>, the supertypeArgs[0].
            if (!of(subtypeParam).isType(supertypeArgs[i], typeVars[i])) {
                return false
            }
        }
        // We only care about the case when the supertype is a non-static inner class
        // in which case we need to make sure the subclass's owner type is a subtype of the
        // supertype's owner.
        return (Modifier.isStatic((supertype.rawType as Class<*>).modifiers)
            || supertype.ownerType == null
            || isOwnedBySubtypeOf(supertype.ownerType))
    }

    private infix fun isSubtypeOfArrayType(supertype: GenericArrayType): Boolean = when (runtimeType) {
        is Class<*> -> {
            val fromClass = runtimeType
            if (!fromClass.isArray) false else of(fromClass.componentType).isSubTypeOf(supertype.genericComponentType)
        }
        is GenericArrayType -> {
            val fromArrayType = runtimeType
            of(fromArrayType.genericComponentType).isSubTypeOf(supertype.genericComponentType)
        }
        else -> false
    }

    private infix fun isSupertypeOfArray(subtype: GenericArrayType): Boolean = when (runtimeType) {
        is Class<*> -> {
            val thisClass = runtimeType
            when {
                !thisClass.isArray -> thisClass.isAssignableFrom(Array<Any>::class.java)
                else -> of(subtype.genericComponentType).isSubTypeOf(thisClass.componentType)
            }
        }
        is GenericArrayType -> of(subtype.genericComponentType).isSubTypeOf(runtimeType.genericComponentType)
        else -> false
    }

    /**
     * `A.is(B)` is defined as `Foo<A>.isSubtypeOf(Foo<B>)`.
     *
     *
     * Specifically, returns true if any of the following conditions is met:
     *
     *
     *  1. 'this' and `formalType` are equal.
     *  1. 'this' and `formalType` have equal canonical form.
     *  1. `formalType` is `<? extends Foo>` and 'this' is a subtype of `Foo`.
     *  1. `formalType` is `<? super Foo>` and 'this' is a supertype of `Foo`.
     *
     *
     * Note that condition 2 isn't technically accurate under the context of a recursively bounded
     * type variables. For example, `Enum<? extends Enum<E>>` canonicalizes to `Enum<?>`
     * where `E` is the type variable declared on the `Enum` class declaration. It's
     * technically *not* true that `Foo<Enum<? extends Enum<E>>>` is a subtype of `Foo<Enum<?>>` according to JLS. See
     * testRecursiveWildcardSubtypeBug() for a real example.
     *
     *
     * It appears that properly handling recursive type bounds in the presence of implicit type
     * bounds is not easy. For now we punt, hoping that this defect should rarely cause issues in real
     * code.
     *
     * @param formalType is `Foo<formalType>` a supertype of `Foo<T>`?
     * @param declaration The type variable in the context of a parameterized type. Used to infer type
     * bound when `formalType` is a wildcard with implicit upper bound.
     */
    private fun isType(formalType: Type, declaration: TypeVariable<*>): Boolean {
        if (runtimeType == formalType) return true
        if (formalType is WildcardType) {
            val your = declaration.canonicalizeWildcardType(formalType)
            // if "formalType" is <? extends Foo>, "this" can be:
            // Foo, SubFoo, <? extends Foo>, <? extends SubFoo>, <T extends Foo> or
            // <T extends SubFoo>.
            // if "formalType" is <? super Foo>, "this" can be:
            // Foo, SuperFoo, <? super Foo> or <? super SuperFoo>.
            return (every(your.upperBounds).isSuperTypeOf(runtimeType) && every(your.lowerBounds).isSubTypeOf(
                runtimeType
            ))
        }
        return runtimeType.canonicalizeWildcardsInType() == formalType.canonicalizeWildcardsInType()
    }

    /**
     * In reflection, `Foo<?>.getUpperBounds()[0]` is always `Object.class`, even when Foo
     * is defined as `Foo<T extends String>`. Thus directly calling `<?>.is(String.class)`
     * will return false. To mitigate, we canonicalize wildcards by enforcing the following
     * invariants:
     *
     *
     *  1. `canonicalize(t)` always produces the equal result for equivalent types. For
     * example both `Enum<?>` and `Enum<? extends Enum<?>>` canonicalize to `Enum<? extends Enum<E>`.
     *  1. `canonicalize(t)` produces a "literal" supertype of t. For example: `Enum<?
     * extends Enum<?>>` canonicalizes to `Enum<?>`, which is a supertype (if we disregard
     * the upper bound is implicitly an Enum too).
     *  1. If `canonicalize(A) == canonicalize(B)`, then `Foo<A>.isSubtypeOf(Foo<B>)`
     * and vice versa. i.e. `A.is(B)` and `B.is(A)`.
     *  1. `canonicalize(canonicalize(A)) == canonicalize(A)`.
     *
     */
    private fun TypeVariable<*>.canonicalizeTypeArg(typeArg: Type): Type? =
        if (typeArg is WildcardType) this.canonicalizeWildcardType(typeArg) else typeArg.canonicalizeWildcardsInType()

    private fun Type.canonicalizeWildcardsInType(): Type = when (this) {
        is ParameterizedType -> this.canonicalizeWildcardsInParameterizedType()
        is GenericArrayType -> this.genericComponentType.canonicalizeWildcardsInType().newArrayType()
        else -> this
    }

    // WARNING: the returned type may have empty upper bounds, which may violate common expectations
    // by user code or even some of our own code. It's fine for the purpose of checking subtypes.
    // Just don't ever let the user access it.
    private fun TypeVariable<*>.canonicalizeWildcardType(type: WildcardType): WildcardType =
        WildcardTypeImpl(type.lowerBounds, type.upperBounds
            .filterNot { any(bounds).isSubTypeOf(it) }
            .map { it.canonicalizeWildcardsInType() }
            .toTypedArray()
        )

    private fun ParameterizedType.canonicalizeWildcardsInParameterizedType(): ParameterizedType {
        val rawType = rawType as Class<*>
        val typeVars = rawType.typeParameters
        val typeArgs = actualTypeArguments
        for (i in typeArgs.indices) typeArgs[i] = typeVars[i].canonicalizeTypeArg(typeArgs[i])
        return ownerType.newParameterizedTypeWithOwner(rawType, typeArgs)
    }

    // Every bound must match. On any false, result is false.
    private fun every(bounds: Array<Type>): Bounds = Bounds(bounds, false)

    // Any bound matches. On any true, result is true.
    private fun any(bounds: Array<Type>): Bounds = Bounds(bounds, true)

    private inner class Bounds internal constructor(private val bounds: Array<Type>, private val target: Boolean) {
        internal infix fun isSubTypeOf(supertype: Type): Boolean =
            if (bounds.any { of(it).isSubTypeOf(supertype) == target }) target else !target

        internal infix fun isSuperTypeOf(subtype: Type): Boolean =
            if (bounds.any { of(subtype).isSubTypeOf(it) == target }) target else !target
    }

    private val rawTypes: Set<Class<in T>>
        get() {
            val s = mutableSetOf<Class<*>>()
            object : TypeVisitor() {
                override fun visitTypeVariable(type: TypeVariable<*>) {
                    visit(*type.bounds)
                }

                override fun visitWildcardType(type: WildcardType) {
                    visit(*type.upperBounds)
                }

                override fun visitParameterizedType(type: ParameterizedType) {
                    s += type.rawType as Class<*>
                }

                override fun visitClass(type: Class<*>) {
                    s += type
                }

                override fun visitGenericArrayType(type: GenericArrayType) {
                    s += of(type.genericComponentType).rawType.arrayClass
                }
            }.visit(runtimeType)
            // Cast from ImmutableSet<Class<?>> to ImmutableSet<Class<? super T>>
            return s.toSet() as Set<Class<in T>>
        }

    private fun isOwnedBySubtypeOf(supertype: Type): Boolean {
        for (type in types) {
            val ownerType = type.getOwnerTypeIfPresent()
            if (ownerType != null && of(ownerType).isSubTypeOf(supertype)) {
                return true
            }
        }
        return false
    }

    /**
     * Returns the owner type of a [ParameterizedType] or enclosing class of a [Class], or
     * null otherwise.
     */
    private fun getOwnerTypeIfPresent(): Type? = when (runtimeType) {
        is ParameterizedType -> runtimeType.ownerType
        is Class<*> -> runtimeType.enclosingClass
        else -> null
    }

    /**
     * Returns the type token representing the generic type declaration of `cls`. For example:
     * `TypeToken.getGenericType(Iterable.class)` returns `Iterable<T>`.
     *
     *
     * If `cls` isn't parameterized and isn't a generic array, the type token of the class is
     * returned.
     */
    // If we are passed with int[].class, don't turn it to GenericArrayType
    @Suppress("UNCHECKED_CAST")
    infix fun <T> toGenericType(cls: Class<T>): TypeToken<T> = when {
        // If we are passed with int[].class, don't turn it to GenericArrayType
        cls.isArray -> of(toGenericType(cls.componentType).runtimeType.newArrayType()) as TypeToken<T>
        else -> {
            val typeParams = cls.typeParameters
            val ownerType = when {
                cls.isMemberClass && !Modifier.isStatic(cls.modifiers) -> toGenericType(cls.enclosingClass).runtimeType
                else -> null
            }

            when {
                typeParams.isNotEmpty() || ownerType != null && ownerType !== cls.enclosingClass -> of(
                    ownerType.newParameterizedTypeWithOwner(cls, typeParams.map { it as Type }.toTypedArray())
                ) as TypeToken<T>
                else -> of(cls)
            }
        }
    }

    private val covariantTypeResolver: TypeResolver?
        get() {
            var resolver = _covariantTypeResolver
            if (resolver == null) {
                _covariantTypeResolver = TypeResolver.covariantly(runtimeType)
                resolver = _covariantTypeResolver
            }
            return resolver
        }

    private val invariantTypeResolver: TypeResolver?
        get() {
            var resolver = _invariantTypeResolver
            if (resolver == null) {
                _invariantTypeResolver = TypeResolver.invariantly(runtimeType)
                resolver = _invariantTypeResolver
            }
            return resolver
        }

    private fun Class<in T>.getSupertypeFromUpperBounds(upperBounds: Array<Type>): TypeToken<in T> {
        for (upperBound in upperBounds) {
            val bound = of(upperBound) as TypeToken<T>// T's upperbound is <? super T>.
            if (bound.isSubTypeOf(this)) {
                return bound.asSuperTypeOf(this as Class<T>)
            }
        }
        throw IllegalArgumentException((this).toString() + " isn't a super type of " + this@TypeToken)
    }

    private fun Class<*>.getSubtypeFromLowerBounds(lowerBounds: Array<Type>): TypeToken<out T> {
        for (lowerBound in lowerBounds) {
            val bound = of(lowerBound) as TypeToken<out T>// T's lower bound is <? extends T>
            // Java supports only one lowerbound anyway.
            return bound.asSubTypeOf(this)
        }
        throw IllegalArgumentException((this).toString() + " isn't a subclass of " + this@TypeToken)
    }

    private val Class<in T>.arraySupertype: TypeToken<in T>
        get() {
            // with component type, we have lost generic type information
            // Use raw type so that compiler allows us to call getSupertype()
            val componentType = this@TypeToken.componentType as TypeToken<T>
            // array is covariant. component type is super type, so is the array type.
            val componentSupertype =
                componentType.asSuperTypeOf(this.componentType as Class<T>)// going from raw type back to generics
            return of(componentSupertype.runtimeType.newArrayClassOrGenericArrayType()) as TypeToken<in T>
        }

    private val Class<*>.arraySubtype: TypeToken<out T>
        get() {
            // array is covariant. component type is subtype, so is the array type.
            val componentSubtype = this@TypeToken.componentType!!.asSubTypeOf(componentType)
            return of(componentSubtype.runtimeType.newArrayClassOrGenericArrayType()) as TypeToken<T>
        }

    private fun Class<*>.resolveTypeArgsForSubclass(): Type {
        // If both runtimeType and subclass are not parameterized, return subclass
        // If runtimeType is not parameterized but subclass is, process subclass as a parameterized type
        // If runtimeType is a raw type (i.e. is a parameterized type specified as a Class<?>), we
        // return subclass as a raw type
        if ((runtimeType is Class<*> && (((typeParameters.isEmpty()) || (rawType.typeParameters.isNotEmpty()))))) {
            // no resolution needed
            return this
        }
        // class Base<A, B> {}
        // class Sub<X, Y> extends Base<X, Y> {}
        // Base<String, Integer>.subtype(Sub.class):

        // Sub<X, Y>.getSupertype(Base.class) => Base<X, Y>
        // => X=String, Y=Integer
        // => Sub<X, Y>=Sub<String, Integer>
        val genericSubtype = toGenericType(this) as TypeToken<T>
        // subclass isn't <? extends T>
        val supertypeWithArgsFromSubtype = genericSubtype.asSuperTypeOf(rawType as Class<T>).runtimeType
        return TypeResolver()
            .where(supertypeWithArgsFromSubtype, runtimeType)
            .resolveType(genericSubtype.runtimeType)
    }

    /**
     * Creates an array class if `componentType` is a class, or else, a [ ]. This is what Java7 does for generic array type parameters.
     */
    private fun Type.newArrayClassOrGenericArrayType(): Type = JavaVersion.JAVA7.newArrayType(this)

    private class SimpleTypeToken<T>(type: Type) : TypeToken<T>(type)

    /**
     * Collects parent types from a sub type.
     *
     * @param <K> The type "kind". Either a TypeToken, or Class.
    </K> */
    private abstract class TypeCollector<K> {
        /** For just classes, we don't have to traverse interfaces.  */
        internal val classesOnly: TypeCollector<K>
            get() = KForwardingTypeCollector()

        inner class KForwardingTypeCollector : ForwardingTypeCollector<K>(this@TypeCollector) {
            override fun getInterfaces(type: K): Iterable<K> = emptySet()

            override fun collectTypes(types: Iterable<K>): List<K> =
                super.collectTypes(types.filterNot { getRawType(it).isInterface })
        }

        internal fun collectTypes(type: K): List<K> = collectTypes(listOf(type))

        internal open fun collectTypes(types: Iterable<K>): List<K> {
            // type -> order number. 1 for Object, 2 for anything directly below, so on so forth.
            val map = hashMapOf<K, Int>()
            for (type in types) collectTypes(type, map)
            return sortKeysByValue(map, naturalOrder<Int>().reversed())
            //return sortKeysByValue(map, Ordering.natural().reverse())
        }

        /**
         * Collects all types to map, and returns the total depth from T up to Object.
         */
        private fun collectTypes(type: K, map: MutableMap<in K, Int>): Int {
            val existing = map[type]
            // short circuit: if set contains type it already contains its supertypes
            if (existing != null) return existing

            // Interfaces should be listed before Object.
            var aboveMe = if (getRawType(type).isInterface) 1 else 0

            for (interfaceType in getInterfaces(type)) aboveMe = Math.max(aboveMe, collectTypes(interfaceType, map))

            val superclass = getSuperclass(type)

            if (superclass != null) aboveMe = Math.max(aboveMe, collectTypes(superclass, map))

            // TODO(benyu): should we include Object for interface? Also, CharSequence[] and Object[] for String[]?
            map[type] = aboveMe + 1
            return aboveMe + 1
        }

        internal abstract fun getRawType(type: K): Class<*>

        internal abstract fun getInterfaces(type: K): Iterable<K>

        internal abstract fun getSuperclass(type: K): K?

        private open class ForwardingTypeCollector<K>(private val delegate: TypeCollector<K>) : TypeCollector<K>() {
            override fun getRawType(type: K): Class<*> = delegate.getRawType(type)

            override fun getInterfaces(type: K): Iterable<K> = delegate.getInterfaces(type)

            override fun getSuperclass(type: K): K? = delegate.getSuperclass(type)
        }

        companion object {
            internal val FOR_GENERIC_TYPE: TypeCollector<TypeToken<*>> = object : TypeCollector<TypeToken<*>>() {
                override fun getRawType(type: TypeToken<*>): Class<*> = type.rawType

                override fun getInterfaces(type: TypeToken<*>): Iterable<TypeToken<*>> = type.genericInterfaces

                override fun getSuperclass(type: TypeToken<*>): TypeToken<*>? = type.genericSuperclass
            }

            internal val FOR_RAW_TYPE: TypeCollector<Class<*>> = object : TypeCollector<Class<*>>() {
                override fun getRawType(type: Class<*>): Class<*> = type

                override fun getInterfaces(type: Class<*>): Iterable<Class<*>> = type.interfaces.toList()

                override fun getSuperclass(type: Class<*>): Class<*>? = type.superclass
            }

            private fun <K, V> sortKeysByValue(map: Map<K, V>, valueComparator: Comparator<in V>): List<K> =
                map.toSortedMap(Comparator { o1, o2 -> valueComparator.compare(map[o1], map[o2]) }).keys.toList()
        }
    }
}

abstract class TypeVisitor {
    private val visited = hashSetOf<Type>()

    /**
     * Visits the given types. Null types are ignored. This allows subclasses to call
     * `visit(parameterizedType.getOwnerType())` safely without having to check nulls.
     */
    fun visit(vararg types: Type?) {
        for (type in types.filterNotNull().filter { visited.add(it) }) {
            var succeeded = false
            try {
                when (type) {
                    is TypeVariable<*> -> visitTypeVariable(type)
                    is WildcardType -> visitWildcardType(type)
                    is ParameterizedType -> visitParameterizedType(type)
                    is Class<*> -> visitClass(type)
                    is GenericArrayType -> visitGenericArrayType(type)
                    else -> throw AssertionError("Unknown type: $type")
                }
                succeeded = true
            } finally {
                // When the visitation failed, we don't want to ignore the second.
                if (!succeeded) visited -= type
            }
        }
    }

    open fun visitClass(type: Class<*>) {}

    open fun visitGenericArrayType(type: GenericArrayType) {}

    open fun visitParameterizedType(type: ParameterizedType) {}

    open fun visitTypeVariable(type: TypeVariable<*>) {}

    open fun visitWildcardType(type: WildcardType) {}
}

class TypeResolver {
    private val typeTable: TypeTable

    constructor() {
        this.typeTable = TypeTable()
    }

    private constructor(typeTable: TypeTable) {
        this.typeTable = typeTable
    }

    /**
     * Returns a new `TypeResolver` with type variables in `formal` mapping to types in `actual`.
     *
     * For example, if `formal` is a `TypeVariable T`, and `actual` is `String.class`, then
     * `new TypeResolver().where(formal, actual)` will [ ][.resolveType] `ParameterizedType List<T>` to
     * `List<String>`, and resolve `Map<T, Something>` to `Map<String, Something>` etc. Similarly, `formal` and
     * `actual` can be `Map<K, V>` and `Map<String, Integer>` respectively, or they
     * can be `E[]` and `String[]` respectively, or even any arbitrary combination
     * thereof.
     *
     * @param formal The type whose type variables or itself is mapped to other type(s). It's almost
     * always a bug if `formal` isn't a type variable and contains no type variable. Make
     * sure you are passing the two parameters in the right order.
     * @param actual The type that the formal type variable(s) are mapped to. It can be or contain yet
     * other type variables, in which case these type variables will be further resolved if
     * corresponding mappings exist in the current `TypeResolver` instance.
     */
    fun where(formal: Type, actual: Type): TypeResolver {
        val mappings = hashMapOf<TypeVariableKey, Type>()
        populateTypeMappings(mappings, formal, actual)
        return where(mappings)
    }

    /** Returns a new `TypeResolver` with `variable` mapping to `type`.  */
    internal infix fun where(mappings: Map<TypeVariableKey, Type>): TypeResolver =
        TypeResolver(typeTable.where(mappings))

    /**
     * Resolves all type variables in `type` and all downstream types and returns a
     * corresponding type with type variables resolved.
     */
    fun resolveType(type: Type): Type = when (type) {
        is TypeVariable<*> -> typeTable.resolve(type)
        is ParameterizedType -> type.resolveParameterizedType()
        is GenericArrayType -> type.resolveGenericArrayType()
        is WildcardType -> type.resolveWildcardType()
        else -> type
    }

    internal fun resolveTypesInPlace(types: Array<Type>): Array<Type> {
        for (i in types.indices) types[i] = resolveType(types[i])
        return types
    }

    private fun resolveTypes(types: Array<Type>): Array<Type> = types.asIterable().mapToTypedArray { resolveType(it) }

    private fun WildcardType.resolveWildcardType(): WildcardType =
        WildcardTypeImpl(resolveTypes(lowerBounds), resolveTypes(upperBounds))

    private fun GenericArrayType.resolveGenericArrayType(): Type =
        resolveType(genericComponentType).newArrayType()

    private fun ParameterizedType.resolveParameterizedType(): ParameterizedType {
        val resolvedOwner = ownerType?.let { resolveType(it) }
        val resolvedRawType = resolveType(rawType)

        val args = actualTypeArguments
        val resolvedArgs = resolveTypes(args)
        return resolvedOwner.newParameterizedTypeWithOwner(resolvedRawType as Class<*>, resolvedArgs)
    }

    /** A TypeTable maintains mapping from [TypeVariable] to types.  */
    private open class TypeTable {
        private val map: Map<TypeVariableKey, Type>

        internal constructor() {
            this.map = emptyMap()
        }

        private constructor(map: Map<TypeVariableKey, Type>) {
            this.map = map
        }

        /**
         * Returns a new `TypeResolver` with `variable` mapping to `type`.
         */
        internal fun where(mappings: Map<TypeVariableKey, Type>): TypeTable {
            val builder = hashMapOf<TypeVariableKey, Type>()
            builder.putAll(map)
            for ((variable, type) in mappings) {
                requireThat(!variable.equalsType(type)) { "Type variable <$variable> bound to itself" }
                builder[variable] = type
            }
            return TypeTable(builder.toMap())
        }

        internal fun resolve(variable: TypeVariable<*>): Type {
            val unguarded = this
            val guarded = object : TypeTable() {
                public override fun resolveInternal(variable: TypeVariable<*>, forDependants: TypeTable): Type {
                    return if (variable.genericDeclaration == variable.genericDeclaration) {
                        variable
                    } else unguarded.resolveInternal(variable, forDependants)
                }
            }
            return resolveInternal(variable, guarded)
        }

        /**
         * Resolves `variable` using the encapsulated type mapping. If it maps to yet another
         * non-reified type or has bounds, `forDependants` is used to do further resolution, which
         * doesn't try to resolve any type variable on generic declarations that are already being
         * resolved.
         *
         *
         * Should only be called and overridden by [.resolve].
         */
        internal open fun resolveInternal(variable: TypeVariable<*>, forDependants: TypeTable): Type {
            val type = map.get(TypeVariableKey(variable))
            if (type == null) {
                val bounds = variable.bounds
                if (bounds.isEmpty()) return variable
                val resolvedBounds = TypeResolver(forDependants).resolveTypes(bounds)
                // We'd like to simply create our own TypeVariable with the newly resolved bounds. There's
                // just one problem: Starting with JDK 7u51, the JDK TypeVariable's equals() method doesn't
                // recognize instances of our TypeVariable implementation. This is a problem because users
                // compare TypeVariables from the JDK against TypeVariables returned by TypeResolver. To
                // work with all JDK versions, TypeResolver must return the appropriate TypeVariable
                // implementation in each of the three possible cases:
                //
                // 1. Prior to JDK 7u51, the JDK TypeVariable implementation interoperates with ours.
                // Therefore, we can always create our own TypeVariable.
                //
                // 2. Starting with JDK 7u51, the JDK TypeVariable implementations does not interoperate
                // with ours. Therefore, we have to be careful about whether we create our own TypeVariable:
                //
                // 2a. If the resolved types are identical to the original types, then we can return the
                // original, identical JDK TypeVariable. By doing so, we sidestep the problem entirely.
                //
                // 2b. If the resolved types are different from the original types, things are trickier. The
                // only way to get a TypeVariable instance for the resolved types is to create our own. The
                // created TypeVariable will not interoperate with any JDK TypeVariable. But this is OK: We
                // don't _want_ our new TypeVariable to be equal to the JDK TypeVariable because it has
                // _different bounds_ than the JDK TypeVariable. And it wouldn't make sense for our new
                // TypeVariable to be equal to any _other_ JDK TypeVariable, either, because any other JDK
                // TypeVariable must have a different declaration or name. The only TypeVariable that our
                // new TypeVariable _will_ be equal to is an equivalent TypeVariable that was also created
                // by us. And that equality is guaranteed to hold because it doesn't involve the JDK
                // TypeVariable implementation at all.
                return when {
                    NativeTypeVariableEquals.NATIVE_TYPE_VARIABLE_ONLY && Arrays.equals(
                        bounds,
                        resolvedBounds
                    ) -> variable
                    else -> newArtificialTypeVariable(variable.genericDeclaration, variable.name, resolvedBounds)
                }
            }

            // in case the type is yet another type variable.
            return TypeResolver(forDependants).resolveType(type)
        }
    }

    private class TypeMappingIntrospector : TypeVisitor() {
        private val mappings = hashMapOf<TypeVariableKey, Type>()

        override fun visitClass(type: Class<*>) {
            visit(type.genericSuperclass)
            visit(*type.genericInterfaces)
        }

        override fun visitParameterizedType(type: ParameterizedType) {
            val rawClass = type.rawType as Class<*>
            val vars = rawClass.typeParameters
            val typeArgs = type.actualTypeArguments
            requireThat(vars.size == typeArgs.size)
            for (i in vars.indices) addToMappings(TypeVariableKey(vars[i]), typeArgs[i])
            visit(rawClass)
            visit(type.ownerType)
        }

        override fun visitTypeVariable(type: TypeVariable<*>) {
            visit(*type.bounds)
        }

        override fun visitWildcardType(type: WildcardType) {
            visit(*type.upperBounds)
        }

        private fun addToMappings(key: TypeVariableKey, arg: Type) {
            // Mapping already established
            // This is possible when following both superClass -> enclosingClass
            // and enclosingclass -> superClass paths.
            // Since we follow the path of superclass first, enclosing second,
            // superclass mapping should take precedence.
            if (mappings.containsKey(key)) return
            // First, check whether key -> arg forms a cycle
            var t: Type? = arg
            while (t != null) {
                if (key.equalsType(t)) {
                    // cycle detected, remove the entire cycle from the mapping so that
                    // each type variable resolves deterministically to itself.
                    // Otherwise, a F -> T cycle will end up resolving both F and T
                    // nondeterministically to either F or T.
                    var x: Type? = arg
                    while (x != null) x = mappings.remove(TypeVariableKey.forLookup(x))
                    return
                }

                t = mappings[TypeVariableKey.forLookup(t)]
            }

            mappings[key] = arg
        }

        companion object {
            /**
             * Returns type mappings using type parameters and type arguments found in the generic
             * superclass and the super interfaces of `contextClass`.
             */
            internal fun getTypeMappings(contextType: Type): Map<TypeVariableKey, Type> {
                val introspector = TypeMappingIntrospector()
                introspector.visit(contextType)
                return introspector.mappings
            }
        }
    }

    // This is needed when resolving types against a context with wildcards
    // For example:
    // class Holder<T> {
    //   void set(T data) {...}
    // }
    // Holder<List<?>> should *not* resolve the set() method to set(List<?> data).
    // Instead, it should create a capture of the wildcard so that set() rejects any List<T>.
    private open class WildcardCapturer private constructor(private val id: AtomicInteger = AtomicInteger()) {
        internal fun capture(type: Type): Type = when (type) {
            is Class<*>, is TypeVariableImpl<*> -> type
            is GenericArrayType -> notForTypeVariable().capture(type.genericComponentType).newArrayType()
            is ParameterizedType -> {
                val rawType = type.rawType as Class<*>
                val typeVars = rawType.typeParameters
                val typeArgs = type.actualTypeArguments
                for (i in typeArgs.indices) typeArgs[i] = forTypeVariable(typeVars[i]).capture(typeArgs[i])
                notForTypeVariable().captureNullable(type.ownerType).newParameterizedTypeWithOwner(
                    rawType,
                    typeArgs
                )
            }
            // ? extends something changes to capture-of
            // TODO(benyu): handle ? super T somehow.
            is WildcardType -> if (type.lowerBounds.isEmpty()) captureAsTypeVariable(type.upperBounds) else type
            else -> throw AssertionError("must have been one of the known types")
        }

        internal open fun captureAsTypeVariable(upperBounds: Array<Type>): TypeVariable<*> = newArtificialTypeVariable(
            WildcardCapturer::class.java,
            "capture#${id.incrementAndGet()}-of ? extends ${upperBounds.joinToString("&")}",
            upperBounds
        )

        private fun forTypeVariable(typeParam: TypeVariable<*>): WildcardCapturer {
            return object : WildcardCapturer(id) {
                override fun captureAsTypeVariable(upperBounds: Array<Type>): TypeVariable<*> {
                    val combined = LinkedHashSet(upperBounds.toList())
                    // Since this is an artifically generated type variable, we don't bother checking
                    // subtyping between declared type bound and actual type bound. So it's possible that we
                    // may generate something like <capture#1-of ? extends Foo&SubFoo>.
                    // Checking subtype between declared and actual type bounds
                    // adds recursive isSubtypeOf() call and feels complicated.
                    // There is no contract one way or another as long as isSubtypeOf() works as expected.
                    combined.addAll(typeParam.bounds.toList())
                    // Object is implicit and only useful if it's the only bound.
                    if (combined.size > 1) combined.remove(Any::class.java)
                    return super.captureAsTypeVariable(combined.toTypedArray())
                }
            }
        }

        private fun notForTypeVariable(): WildcardCapturer = WildcardCapturer(id)

        private fun captureNullable(type: Type?): Type? = type?.let { capture(it) }

        companion object {
            internal val INSTANCE = WildcardCapturer()
        }
    }

    /**
     * Wraps around `TypeVariable<?>` to ensure that any two type variables are equal as long as
     * they are declared by the same [java.lang.reflect.GenericDeclaration] and have the same
     * name, even if their bounds differ.
     *
     * While resolving a type variable from a `var -> type` map, we don't care whether the
     * type variable's bound has been partially resolved. As long as the type variable "identity"
     * matches.
     *
     * On the other hand, if for example we are resolving `List<A extends B>` to `List<A extends String>`, we need to
     * compare that `<A extends B>` is unequal to `<A extends String>` in order to decide to use the transformed type
     * instead of the original type.
     */
    internal class TypeVariableKey(private val variable: TypeVariable<*>) {
        override fun hashCode(): Int = Objects.hash(variable.genericDeclaration, variable.name)

        override fun equals(other: Any?): Boolean =
            if (other is TypeVariableKey) equalsTypeVariable(other.variable) else false

        override fun toString(): String = variable.toString()

        /**
         * Returns true if `type` is a `TypeVariable` with the same name and declared by the
         * same `GenericDeclaration`.
         */
        fun equalsType(type: Type): Boolean = (type as? TypeVariable<*>)?.let { equalsTypeVariable(it) } ?: false

        private fun equalsTypeVariable(that: TypeVariable<*>): Boolean =
            variable.genericDeclaration == that.genericDeclaration && variable.name == that.name

        companion object {
            /** Wraps `t` in a `TypeVariableKey` if it's a type variable.  */
            fun forLookup(t: Type): TypeVariableKey? = (t as? TypeVariable<*>)?.let { TypeVariableKey(it) }
        }
    }

    companion object {
        /**
         * Returns a resolver that resolves types "covariantly".
         *
         *
         * For example, when resolving `List<T>` in the context of `ArrayList<?>`, `<T>` is covariantly resolved to
         * `<?>` such that return type of `List::get` is `<?>`.
         */
        infix fun covariantly(contextType: Type): TypeResolver =
            TypeResolver().where(TypeMappingIntrospector.getTypeMappings(contextType))

        /**
         * Returns a resolver that resolves types "invariantly".
         *
         * For example, when resolving `List<T>` in the context of `ArrayList<?>`, `<T>` cannot be invariantly resolved
         * to `<?>` because otherwise the parameter type of
         * `List::set` will be `<?>` and it'll falsely say any object can be passed into
         * `ArrayList<?>::set`.
         *
         * Instead, `<?>` will be resolved to a capture in the form of a type variable `<capture-of-? extends Object>`,
         * effectively preventing `set` from accepting any type.
         */
        infix fun invariantly(contextType: Type): TypeResolver {
            val invariantContext = WildcardCapturer.INSTANCE.capture(contextType)
            return TypeResolver().where(TypeMappingIntrospector.getTypeMappings(invariantContext))
        }

        private fun populateTypeMappings(
            mappings: MutableMap<TypeVariableKey, Type>, from: Type, to: Type
        ) {
            if (from == to) return
            object : TypeVisitor() {
                override fun visitTypeVariable(type: TypeVariable<*>) {
                    mappings[TypeVariableKey(type)] = to
                }

                override fun visitWildcardType(type: WildcardType) {
                    // okay to say <?> is anything
                    if (to !is WildcardType) return

                    val fromUpperBounds = type.upperBounds
                    val toUpperBounds = to.upperBounds
                    val fromLowerBounds = type.lowerBounds
                    val toLowerBounds = to.lowerBounds

                    requireThat(
                        fromUpperBounds.size == toUpperBounds.size && fromLowerBounds.size == toLowerBounds.size
                    ) { "Incompatible type: <$type> vs. <$to>" }

                    for (i in fromUpperBounds.indices) populateTypeMappings(
                        mappings,
                        fromUpperBounds[i],
                        toUpperBounds[i]
                    )
                    for (i in fromLowerBounds.indices) populateTypeMappings(
                        mappings,
                        fromLowerBounds[i],
                        toLowerBounds[i]
                    )
                }

                override fun visitParameterizedType(type: ParameterizedType) {
                    // Okay to say Foo<A> is <?>
                    if (to is WildcardType) return

                    val toParameterizedType = ParameterizedType::class.java.expectArgument(to)
                    if (type.ownerType != null && toParameterizedType.ownerType != null) populateTypeMappings(
                        mappings,
                        type.ownerType,
                        toParameterizedType.ownerType
                    )

                    requireThat(type.rawType == toParameterizedType.rawType) { "Inconsistent raw type: <$type> vs. <$to>" }

                    val fromArgs = type.actualTypeArguments
                    val toArgs = toParameterizedType.actualTypeArguments

                    requireThat(fromArgs.size == toArgs.size) { "<$type> not compatible with <$toParameterizedType>" }

                    for (i in fromArgs.indices) populateTypeMappings(mappings, fromArgs[i], toArgs[i])
                }

                override fun visitGenericArrayType(type: GenericArrayType) {
                    // Okay to say A[] is <?>
                    if (to is WildcardType) return
                    val componentType = to.componentType

                    requireThat(componentType != null) { "<$to> is not an array type." }

                    populateTypeMappings(mappings, type.genericComponentType, componentType)
                }

                override fun visitClass(type: Class<*>) {
                    // Okay to say Foo is <?>
                    if (to is WildcardType) return
                    // Can't map from a raw class to anything other than itself or a wildcard.
                    // You can't say "assuming String is Integer".
                    // And we don't support "assuming String is T"; user has to say "assuming T is String".
                    throw IllegalArgumentException("No type mapping from $type to $to")
                }
            }.visit(from)
        }

        private fun <T> Class<T>.expectArgument(arg: Any?): T = try {
            cast(arg)
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(arg.toString() + " is not a " + simpleName)
        }
    }
}

@PublishedApi
internal inline fun <reified Type> typeTokenOf() = object : TypeToken<Type>() {}

// -- UTILITY FUNCTIONS -- \\
/** Class#toString without the "class " and "interface " prefixes  */
private val TYPE_NAME = fun(from: Type): String = JavaVersion.CURRENT.typeName(from)

/** Returns the array type of `componentType`.  */
internal fun Type.newArrayType(): Type {
    if (this is WildcardType) {
        val lowerBounds = lowerBounds
        requireThat(lowerBounds.size <= 1) { "Wildcard cannot have more than one lower bounds." }
        return if (lowerBounds.size == 1) {
            lowerBounds[0].newArrayType().supertypeOf()
        } else {
            val upperBounds = upperBounds
            requireThat(upperBounds.size == 1) { "Wildcard should have only one upper bound." }
            upperBounds[0].newArrayType().subtypeOf()
        }
    }
    return JavaVersion.CURRENT.newArrayType(this)
}

/**
 * Returns a type where `rawType` is parameterized by `arguments` and is owned by
 * `ownerType`.
 */
internal fun Type?.newParameterizedTypeWithOwner(
    rawType: Class<*>,
    arguments: Array<Type>
): ParameterizedType {
    if (this == null) return rawType.newParameterizedType(arguments)
    // ParameterizedTypeImpl constructor already checks, but we want to throw NPE before IAE
    requireThat(rawType.enclosingClass != null) { "Owner type for unenclosed $rawType" }
    return ParameterizedTypeImpl(this, rawType, arguments)
}

/** Returns a type where `rawType` is parameterized by `arguments`.  */
internal fun Class<*>.newParameterizedType(arguments: Array<Type>): ParameterizedType =
    ParameterizedTypeImpl(ClassOwnership.JVM_BEHAVIOR.getOwnerType(this), this, arguments)

/** Decides what owner type to use for constructing [ParameterizedType] from a raw class.  */
private enum class ClassOwnership {
    OWNED_BY_ENCLOSING_CLASS {
        override fun getOwnerType(rawType: Class<*>): Class<*> = rawType.enclosingClass
    },
    LOCAL_CLASS_HAS_NO_OWNER {
        override fun getOwnerType(rawType: Class<*>): Class<*>? =
            if (rawType.isLocalClass) null else rawType.enclosingClass
    };

    abstract fun getOwnerType(rawType: Class<*>): Class<*>?

    companion object {
        internal val JVM_BEHAVIOR = detectJvmBehavior()

        private fun detectJvmBehavior(): ClassOwnership {
            open class LocalClass<T>

            val subclass = object : LocalClass<String>() {}.javaClass
            val parameterizedType = subclass.genericSuperclass as ParameterizedType
            for (behavior in values()) {
                if (behavior.getOwnerType(LocalClass::class.java) == parameterizedType.ownerType) return behavior
            }
            throw AssertionError()
        }
    }
}

/**
 * Returns a new [TypeVariable] that belongs to `declaration` with `name` and
 * `bounds`.
 */
internal fun <D : GenericDeclaration> newArtificialTypeVariable(
    declaration: D,
    name: String,
    bounds: Array<Type>
): TypeVariable<D> =
    declaration.newTypeVariableImpl(name, if (bounds.isEmpty()) arrayOf<Type>(Any::class.java) else bounds)

/** Returns a new [WildcardType] with `upperBound`.  */
internal fun Type.subtypeOf(): WildcardType = WildcardTypeImpl(createArray(0), arrayOf(this))

/** Returns a new [WildcardType] with `lowerBound`.  */
internal fun Type.supertypeOf(): WildcardType = WildcardTypeImpl(arrayOf(this), arrayOf(Any::class.java))

/**
 * Returns human readable string representation of `type`.
 *
 *
 * The format is subject to change.
 */
internal val Type.prettyString: String
    get() = if (this is Class<*>) this.name else toString()

internal val Type.componentType: Type?
    get() {
        val result = AtomicReference<Type>()
        object : TypeVisitor() {
            override fun visitTypeVariable(type: TypeVariable<*>) {
                result.set(subtypeOfComponentType(type.bounds))
            }

            override fun visitWildcardType(type: WildcardType) {
                result.set(subtypeOfComponentType(type.upperBounds))
            }

            override fun visitGenericArrayType(type: GenericArrayType) {
                result.set(type.genericComponentType)
            }

            override fun visitClass(type: Class<*>) {
                result.set(type.componentType)
            }
        }.visit(this)
        return result.get()
    }

/**
 * Returns `? extends X` if any of `bounds` is a subtype of `X[]`; or `null` otherwise.
 */
private fun subtypeOfComponentType(bounds: Array<Type>): Type? {
    for (componentType in bounds.mapNotNull { it.componentType }) {
        // Only the first bound can be a class or array.
        // Bounds after the first can only be interfaces.
        return when {
            componentType is Class<*> && componentType.isPrimitive -> componentType
            else -> componentType.subtypeOf()
        }
    }
    return null
}

private class GenericArrayTypeImpl(componentType: Type) : GenericArrayType, Serializable {
    private val componentType: Type = JavaVersion.CURRENT.usedInGenericType(componentType)

    override fun getGenericComponentType(): Type = componentType

    override fun toString(): String = componentType.prettyString + "[]"

    override fun hashCode(): Int = componentType.hashCode()

    override fun equals(other: Any?): Boolean =
        if (other is GenericArrayType) genericComponentType == other.genericComponentType else false
}

private class ParameterizedTypeImpl(
    private val ownerType: Type?,
    private val rawType: Class<*>,
    typeArguments: Array<Type>
) : ParameterizedType, Serializable {
    private val argumentsList: List<Type>

    init {
        requireThat(typeArguments.size == rawType.typeParameters.size)
        typeArguments.disallowPrimitiveType("type parameter")
        this.argumentsList = JavaVersion.CURRENT.usedInGenericType(typeArguments)
    }

    override fun getActualTypeArguments(): Array<Type> = argumentsList.toArray()

    override fun getRawType(): Type = rawType

    override fun getOwnerType(): Type? = ownerType

    override fun toString(): String = buildString {
        if (ownerType != null && JavaVersion.CURRENT.jdkTypeDuplicatesOwnerName()) append(
            "${JavaVersion.CURRENT.typeName(
                ownerType
            )}."
        )
        append(rawType.name)
        append('<')
        append(argumentsList.joinToString(transform = TYPE_NAME))
        append('>')
    }

    override fun hashCode(): Int = ((ownerType?.hashCode() ?: 0) xor argumentsList.hashCode() xor rawType.hashCode())

    override fun equals(other: Any?): Boolean = when (other) {
        !is ParameterizedType -> false
        else -> getRawType() == other.rawType && getOwnerType() == other.ownerType && Arrays.equals(
            actualTypeArguments,
            other.actualTypeArguments
        )
    }
}

/**
 * Returns a proxy instance that implements `interfaceType` by dispatching method
 * invocations to `handler`. The class loader of `interfaceType` will be used to
 * define the proxy class. To implement multiple interfaces or specify a class loader, use [ ][Proxy.newProxyInstance].
 *
 * @throws IllegalArgumentException if `interfaceType` does not specify the type of a Java interface
 */
private fun <T> Class<T>.newProxy(handler: InvocationHandler): T {
    requireThat(isInterface) { "${this} is not an interface" }
    val new = Proxy.newProxyInstance(classLoader, arrayOf<Class<*>>(this), handler)
    return cast(new)
}

private fun <D : GenericDeclaration> D.newTypeVariableImpl(
    name: String,
    bounds: Array<Type>
): TypeVariable<D> {
    val typeVariableImpl = TypeVariableImpl(this, name, bounds)
    return TypeVariable::class.java.newProxy(TypeVariableInvocationHandler(typeVariableImpl)) as TypeVariable<D>
}

/**
 * Invocation handler to work around a compatibility problem between Java 7 and Java 8.
 *
 *
 * Java 8 introduced a new method `getAnnotatedBounds()` in the [TypeVariable]
 * interface, whose return type `AnnotatedType[]` is also new in Java 8. That means that we
 * cannot implement that interface in source code in a way that will compile on both Java 7 and
 * Java 8. If we include the `getAnnotatedBounds()` method then its return type means it
 * won't compile on Java 7, while if we don't include the method then the compiler will complain
 * that an abstract method is unimplemented. So instead we use a dynamic proxy to get an
 * implementation. If the method being called on the `TypeVariable` instance has the same
 * name as one of the public methods of [TypeVariableImpl], the proxy calls the same method
 * on its instance of `TypeVariableImpl`. Otherwise it throws [ ]; this should only apply to `getAnnotatedBounds()`. This
 * does mean that users on Java 8 who obtain an instance of `TypeVariable` from [ ][TypeResolver.resolveType] will not be able to call `getAnnotatedBounds()` on it, but that
 * should hopefully be rare.
 *
 *
 * This workaround should be removed at a distant future time when we no longer support Java
 * versions earlier than 8.
 */
private class TypeVariableInvocationHandler(internal val typeVariableImpl: TypeVariableImpl<*>) : InvocationHandler {
    override fun invoke(proxy: Any, method: Method, args: Array<Any>): Any {
        val methodName = method.name
        val typeVariableMethod = typeVariableMethods[methodName]
        return if (typeVariableMethod == null) UNSUPPORTED(methodName) else try {
            typeVariableMethod.invoke(typeVariableImpl, args)
        } catch (e: InvocationTargetException) {
            throw e.cause!!
        }
    }

    companion object {
        private val typeVariableMethods: Map<String, Method> = TypeVariableImpl::class.java.methods
            .filter { it.declaringClass == TypeVariableImpl::class.java }.associateBy {
                try {
                    it.isAccessible = true
                } catch (e: AccessControlException) {
                    // OK: the method is accessible to us anyway. The setAccessible call is only for
                    // unusual execution environments where that might not be true.
                }

                it.name
            }
    }
}

private class TypeVariableImpl<D : GenericDeclaration>(
    val genericDeclaration: D,
    val typeName: String,
    bounds: Array<Type>
) {
    init {
        bounds.disallowPrimitiveType("bound for type variable")
    }

    private val internalBounds: List<Type> = bounds.toList()

    fun getBounds(): Array<Type> = internalBounds.toArray()

    override fun toString(): String = typeName

    override fun hashCode(): Int = genericDeclaration.hashCode() xor typeName.hashCode()

    override fun equals(other: Any?): Boolean {
        if (NativeTypeVariableEquals.NATIVE_TYPE_VARIABLE_ONLY) {
            // equal only to our TypeVariable implementation with identical bounds
            if (other != null
                && Proxy.isProxyClass(other.javaClass)
                && Proxy.getInvocationHandler(other) is TypeVariableInvocationHandler
            ) {
                val typeVariableInvocationHandler = Proxy.getInvocationHandler(other) as TypeVariableInvocationHandler
                val that = typeVariableInvocationHandler.typeVariableImpl
                return (typeName == that.typeName
                    && genericDeclaration == that.genericDeclaration
                    && internalBounds == that.internalBounds)
            }
            return false
        } else {
            // equal to any TypeVariable implementation regardless of bounds
            if (other is TypeVariable<*>) {
                val that = other as TypeVariable<*>?
                return typeName == that!!.name && genericDeclaration == that.genericDeclaration
            }
            return false
        }
    }
}

internal class WildcardTypeImpl(lowerBounds: Array<Type>, upperBounds: Array<Type>) : WildcardType, Serializable {
    private val lowerBounds: List<Type>
    private val upperBounds: List<Type>

    init {
        lowerBounds.disallowPrimitiveType("lower bound for wildcard")
        upperBounds.disallowPrimitiveType("upper bound for wildcard")
        this.lowerBounds = JavaVersion.CURRENT.usedInGenericType(lowerBounds)
        this.upperBounds = JavaVersion.CURRENT.usedInGenericType(upperBounds)
    }

    override fun getLowerBounds(): Array<Type> = lowerBounds.toArray()

    override fun getUpperBounds(): Array<Type> = upperBounds.toArray()

    override fun equals(other: Any?): Boolean =
        if (other is WildcardType) lowerBounds == other.lowerBounds.toList() && upperBounds == other.upperBounds.toList() else false

    override fun hashCode(): Int = lowerBounds.hashCode() xor upperBounds.hashCode()

    override fun toString(): String = buildString {
        append('?')
        for (lowerBound in lowerBounds) append(" super ").append(JavaVersion.CURRENT.typeName(lowerBound))
        for (upperBound in upperBounds.filterUpperBounds()) append(" extends ").append(
            JavaVersion.CURRENT.typeName(
                upperBound
            )
        )
    }
}

private fun Collection<Type>.toArray(): Array<Type> = toTypedArray()

private fun Iterable<Type>.filterUpperBounds(): Iterable<Type> = filterNot { it == Any::class.java }

private fun Array<Type>.disallowPrimitiveType(usedAs: String) {
    for (type in filterIsInstance<Class<*>>()) requireThat(!type.isPrimitive) { "Primitive type <$type> used as <$usedAs>" }
}

// TODO(user): This is not the most efficient way to handle generic
// arrays, but is there another way to extract the array class in a
// non-hacky way (i.e. using String value class names- "[L...")?
/** Returns the `Class` object of arrays with `componentType`.  */
internal val Class<*>.arrayClass: Class<*>
    get() = ArrayType.newInstance(this, 0).javaClass

// TODO(benyu): Once behavior is the same for all Java versions we support, delete this.
private enum class JavaVersion {
    JAVA6 {
        override fun newArrayType(componentType: Type): GenericArrayType = GenericArrayTypeImpl(componentType)

        override fun usedInGenericType(type: Type): Type =
            if (type is Class<*> && type.isArray) GenericArrayTypeImpl(type.componentType) else type
    },
    JAVA7 {
        override fun newArrayType(componentType: Type): Type =
            (componentType as? Class<*>)?.let { it.arrayClass } ?: GenericArrayTypeImpl(componentType)

        override fun usedInGenericType(type: Type): Type = type
    },
    JAVA8 {
        override fun newArrayType(componentType: Type): Type = JAVA7.newArrayType(componentType)

        override fun usedInGenericType(type: Type): Type = JAVA7.usedInGenericType(type)

        override fun typeName(type: Type): String {
            try {
                val getTypeName = Type::class.java.getMethod("getTypeName")
                return getTypeName.invoke(type) as String
            } catch (e: NoSuchMethodException) {
                throw AssertionError("Type.getTypeName should be available in Java 8")
            } catch (e: InvocationTargetException) {
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            }

        }
    },
    JAVA9 {
        override fun newArrayType(componentType: Type): Type = JAVA8.newArrayType(componentType)

        override fun usedInGenericType(type: Type): Type = JAVA8.usedInGenericType(type)

        override fun typeName(type: Type): String = JAVA8.typeName(type)

        override fun jdkTypeDuplicatesOwnerName(): Boolean = false
    };

    abstract fun newArrayType(componentType: Type): Type

    abstract fun usedInGenericType(type: Type): Type

    open fun usedInGenericType(types: Array<Type>): List<Type> = types.map { usedInGenericType(it) }

    open fun typeName(type: Type): String = type.prettyString

    open fun jdkTypeDuplicatesOwnerName(): Boolean = true

    companion object {
        val CURRENT: JavaVersion = when {
            AnnotatedElement::class.java.isAssignableFrom(TypeVariable::class.java) -> when {
                JavaVersionHelper().capture().toString().contains("java.util.Map.java.util.Map") -> JAVA8
                else -> JAVA9
            }
            IntArrayTypeCapture().capture() is Class<*> -> JAVA7
            else -> JAVA6
        }

        private class IntArrayTypeCapture : TypeCapture<IntArray>()
        private class JavaVersionHelper : TypeCapture<Map.Entry<String, Array<IntArray>>>()
    }
}

/**
 * Per [issue 1635](https://code.google.com/p/guava-libraries/issues/detail?id=1635),
 * In JDK 1.7.0_51-b13, [TypeVariableImpl.equals] is changed to no longer be equal
 * to custom TypeVariable implementations. As a result, we need to make sure our TypeVariable
 * implementation respects symmetry. Moreover, we don't want to reconstruct a native type variable
 * `<A>` using our implementation unless some of its bounds have changed in resolution. This
 * avoids creating unequal TypeVariable implementation unnecessarily. When the bounds do change,
 * however, it's fine for the synthetic TypeVariable to be unequal to any native TypeVariable
 * anyway.
 */
private class NativeTypeVariableEquals<X> {
    companion object {
        val NATIVE_TYPE_VARIABLE_ONLY =
            NativeTypeVariableEquals::class.java.typeParameters[0] != newArtificialTypeVariable(
                NativeTypeVariableEquals::class.java,
                "X",
                emptyArray()
            )
    }
}