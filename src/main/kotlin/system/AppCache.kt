package system

import de.fruxz.ascend.extension.empty
import de.fruxz.sparkle.framework.infrastructure.app.AppCache
import de.fruxz.sparkle.framework.infrastructure.app.cache.CacheDepthLevel
import java.util.*

object AppCache : AppCache {

    override fun dropEntityData(entityIdentity: UUID, dropDepth: CacheDepthLevel) = empty()

    override fun dropEverything(dropDepth: CacheDepthLevel) = empty()

}