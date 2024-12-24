package com.huaban.analysis.jieba

import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 词典树分段，表示词典树的一个分枝
 */
internal class DictSegment(
    // 当前节点上存储的字符
    private val nodeChar: Char,
) : Comparable<DictSegment> {

    private val childrenSegment: MutableMap<Char, DictSegment> by lazy { ConcurrentHashMap() }

    // 数组方式存储结构
    private var childrenArray: Array<DictSegment?>? = null

    // 当前节点存储的 Segment 数目
    // storeSize <= ARRAY_LENGTH_LIMIT, 使用数组存储
    // storeSize > ARRAY_LENGTH_LIMIT, 则使用 Map 存储
    private var storeSize = 0

    // 当前 DictSegment 状态
    // 默认 0, 1 表示从根节点到当前节点的路径表示一个词
    private var nodeState = 0

    // 判断是否有下一个节点
    private fun hasNextNode() = this.storeSize > 0

    /**
     * 匹配词段
     */
    @JvmOverloads
    fun match(charArray: CharArray, begin: Int = 0, length: Int = charArray.size, searchHit: Hit? = null): Hit {
        var niceSearchHit = searchHit
        if (niceSearchHit == null) {
            // 如果hit为空，新建
            niceSearchHit = Hit()
            // 设置hit的其实文本位置
            niceSearchHit.begin = begin
        } else {
            // 否则要将HIT状态重置
            niceSearchHit.setUnmatch()
        }
        // 设置hit的当前处理位置
        niceSearchHit.end = begin

        val keyChar = charArray[begin]
        var ds: DictSegment? = null

        // 引用实例变量为本地变量，避免查询时遇到更新的同步问题
        val segmentArray = this.childrenArray
        val segmentMap: Map<Char, DictSegment> = this.childrenSegment

        // STEP1 在节点中查找keyChar对应的DictSegment
        if (segmentArray != null) {
            // 在数组中查找
            val keySegment = DictSegment(keyChar)
            val position = Arrays.binarySearch(segmentArray, 0, this.storeSize, keySegment)
            if (position >= 0) {
                ds = segmentArray[position]
            }
        } else {
            // 在map中查找
            ds = segmentMap[keyChar]
        }

        // STEP2 找到DictSegment，判断词的匹配状态，是否继续递归，还是返回结果
        if (ds != null) {
            if (length > 1) {
                // 词未匹配完，继续往下搜索
                return ds.match(charArray, begin + 1, length - 1, niceSearchHit)
            } else if (length == 1) {
                // 搜索最后一个char

                if (ds.nodeState == 1) {
                    // 添加HIT状态为完全匹配
                    niceSearchHit.setMatch()
                }
                if (ds.hasNextNode()) {
                    // 添加HIT状态为前缀匹配
                    niceSearchHit.setPrefix()
                    // 记录当前位置的DictSegment
                    niceSearchHit.matchedDictSegment = ds
                }
                return niceSearchHit
            }
        }
        // STEP3 没有找到DictSegment， 将HIT设置为不匹配
        return niceSearchHit
    }

    /**
     * 加载填充词典片段
     */
    private fun fillSegment(charArray: CharArray) {
        this.fillSegment(charArray, 0, charArray.size, 1)
    }

    /**
     * 屏蔽词典中的一个词
     */
    @Suppress("unused")
    fun disableSegment(charArray: CharArray) {
        this.fillSegment(charArray, 0, charArray.size, 0)
    }

    /**
     * 加载填充词典片段
     */
    @Synchronized
    private fun fillSegment(charArray: CharArray, begin: Int, length: Int, enabled: Int) {
        // 获取字典表中的汉字对象
        val beginChar = charArray[begin]
        var keyChar = charMap[beginChar]
        // 字典中没有该字，则将其添加入字典
        if (keyChar == null) {
            charMap[beginChar] = beginChar
            keyChar = beginChar
        }

        // 搜索当前节点的存储，查询对应keyChar的keyChar，如果没有则创建
        val ds = lookforSegment(keyChar, enabled)
        if (ds != null) {
            // 处理keyChar对应的segment
            if (length > 1) {
                // 词元还没有完全加入词典树
                ds.fillSegment(charArray, begin + 1, length - 1, enabled)
            } else if (length == 1) {
                // 已经是词元的最后一个char,设置当前节点状态为enabled，
                // enabled=1表明一个完整的词，enabled=0表示从词典中屏蔽当前词
                ds.nodeState = enabled
            }
        }
    }

    /**
     * 查找本节点下对应的keyChar的segment *
     *
     * @param keyChar
     * @param create
     * =1如果没有找到，则创建新的segment ; =0如果没有找到，不创建，返回null
     * @return
     */
    private fun lookforSegment(keyChar: Char, create: Int): DictSegment? {
        var ds: DictSegment? = null

        if (this.storeSize <= ARRAY_LENGTH_LIMIT) {
            // 获取数组容器，如果数组未创建则创建数组
            val segmentArray = getChildrenArray()
            // 搜寻数组
            val keySegment = DictSegment(keyChar)
            val position = Arrays.binarySearch(segmentArray, 0, this.storeSize, keySegment)
            if (position >= 0) {
                ds = segmentArray[position]
            }

            // 遍历数组后没有找到对应的segment
            if (ds == null && create == 1) {
                ds = keySegment
                if (this.storeSize < ARRAY_LENGTH_LIMIT) {
                    // 数组容量未满，使用数组存储
                    segmentArray[storeSize] = ds
                    // segment数目+1
                    storeSize++
                    Arrays.sort(segmentArray, 0, this.storeSize)
                } else {
                    // 数组容量已满，切换Map存储
                    // 获取Map容器，如果Map未创建,则创建Map
                    val segmentMap = getChildrenMap()
                    // 将数组中的segment迁移到Map中
                    migrate(segmentArray, segmentMap)
                    // 存储新的segment
                    segmentMap[keyChar] = ds
                    // segment数目+1 ， 必须在释放数组前执行storeSize++ ， 确保极端情况下，不会取到空的数组
                    storeSize++
                    // 释放当前的数组引用
                    this.childrenArray = null
                }
            }
        } else {
            // 获取Map容器，如果Map未创建,则创建Map
            val segmentMap = getChildrenMap()
            // 搜索Map
            ds = segmentMap[keyChar]
            if (ds == null && create == 1) {
                // 构造新的segment
                ds = DictSegment(keyChar)
                segmentMap[keyChar] = ds
                // 当前节点存储segment数目+1
                storeSize++
            }
        }

        return ds
    }

    /**
     * 获取数组容器 线程同步方法
     */
    private fun getChildrenArray(): Array<DictSegment?> {
        if (this.childrenArray == null) {
            synchronized(this) {
                if (this.childrenArray == null) {
                    this.childrenArray = arrayOfNulls(ARRAY_LENGTH_LIMIT)
                }
            }
        }
        return childrenArray!!
    }

    private fun getChildrenMap(): MutableMap<Char, DictSegment> {
        return childrenSegment
    }

    /**
     * 将数组中的segment迁移到Map中以支持高性能存储
     */
    private fun migrate(segmentArray: Array<DictSegment?>, segmentMap: MutableMap<Char, DictSegment>) {
        for (segment in segmentArray) {
            if (segment != null) {
                segmentMap[segment.nodeChar] = segment
            }
        }
    }

    /**
     * 实现Comparable接口
     */
    override fun compareTo(other: DictSegment): Int {
        // 对当前节点存储的char进行比较
        return nodeChar.compareTo(other.nodeChar)
    }

    fun fillSegments(words: List<String>) {
        for (word in words) {
            fillSegment(word.toCharArray())
        }
    }

    companion object {
        // 公用字典表，存储汉字
        private val charMap: MutableMap<Char, Char> = HashMap(16, 0.95f)

        // 数组大小上限
        private const val ARRAY_LENGTH_LIMIT = 3
    }

}