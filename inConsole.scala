import org.chipsalliance.cde.config._

val p : Parameters = new Config(new playground.PlaygroundConfig)

import freechips.rocketchip.subsystem._

val tileParams = p(TilesLocated(InSubsystem)).filter(_.isInstanceOf[RocketTileAttachParams])

val numTile = tileParams.length

val rocketTiles = tileParams.filter(_.isInstanceOf[RocketTileAttachParams])

val numRocketTiles = rocketTiles.length

rocketTiles.map(i => i.tileParams).map{x => println(s"core ${x.tileId} word-length : ${x.core.xLen} ")}

val param = rocketTiles.head.tileParams
