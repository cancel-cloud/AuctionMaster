package system

import de.fruxz.sparkle.framework.infrastructure.app.AppCache
import de.fruxz.sparkle.framework.infrastructure.app.cache.CacheDepthLevel
import java.util.*

object AppCache : AppCache {

    override fun dropEntityData(entityIdentity: UUID, dropDepth: CacheDepthLevel) {

    }

    override fun dropEverything(dropDepth: CacheDepthLevel) {

    }
}