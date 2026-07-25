package org.autojs.autojs.runtime.api.augment.formatter

import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey

object Formatter : Augmentable() {

    override val keys = listOf(AugmentableKey("fmt"))

}
