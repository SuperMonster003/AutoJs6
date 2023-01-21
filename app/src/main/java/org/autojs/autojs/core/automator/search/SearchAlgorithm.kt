package org.autojs.autojs.core.automator.search

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.filter.Filter

interface SearchAlgorithm {

    fun search(root: UiObject, filter: Filter, limit: Int = Int.MAX_VALUE): ArrayList<UiObject>

}