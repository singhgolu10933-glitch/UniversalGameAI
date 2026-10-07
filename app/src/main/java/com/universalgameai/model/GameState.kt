package com.universalgameai.model

import com.universalgameai.vision.VisionResult

enum class GameType {
    UNKNOWN,
    MENU,
    PUZZLE_2D,
    ARCADE_2D,
    PLATFORMER_2D,
    CARD_GAME_2D,
    BOARD_GAME_2D,
    RACING_3D,
    ACTION_3D,
    ADVENTURE_3D,
    OTHER_3D
}

enum class GamePhase {
    UNKNOWN,
    LOADING,
    MENU,
    PLAYING,
    PAUSED,
    GAME_OVER,
    VICTORY
}

data class ScreenRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left >= 0f)
        require(top >= 0f)
        require(right >= left)
        require(bottom >= top)
    }

    val width: Float
        get() = right - left

    val height: Float
        get() = bottom - top

    val centerX: Float
        get() = left + width / 2f

    val centerY: Float
        get() = top + height / 2f
}

data class DetectedObject(
    val id: Long,
    val label: String,
    val confidence: Float,
    val region: ScreenRegion,
    val interactable: Boolean
)

data class GameState(
    val timestampMs: Long,
    val screenWidth: Int,
    val screenHeight: Int,

    val gameType: GameType,
    val phase: GamePhase,

    val vision: VisionResult,

    val detectedObjects: List<DetectedObject>,

    val interactiveRegions: List<ScreenRegion>,

    val stateConfidence: Float
) {

    val isPlayable: Boolean
        get() =
            phase == GamePhase.PLAYING &&
            stateConfidence > 0.20f

    companion object {

        fun fromVision(
            vision: VisionResult
        ): GameState {

            val confidence =
                calculateInitialConfidence(
                    vision
                )

            return GameState(
                timestampMs =
                    System.currentTimeMillis(),

                screenWidth =
                    vision.width,

                screenHeight =
                    vision.height,

                gameType =
                    GameType.UNKNOWN,

                phase =
                    GamePhase.UNKNOWN,

                vision =
                    vision,

                detectedObjects =
                    emptyList(),

                interactiveRegions =
                    emptyList(),

                stateConfidence =
                    confidence
            )
        }

        private fun calculateInitialConfidence(
            vision: VisionResult
        ): Float {

            if (!vision.hasVisualContent) {
                return 0f
            }

            /*
             * This is only a baseline confidence.
             *
             * It does NOT claim that we already understand
             * the game. Actual game understanding will come
             * from the object/state analysis layer.
             */

            var confidence = 0.15f

            if (vision.averageBrightness > 0.03f) {
                confidence += 0.10f
            }

            if (vision.edgeDensity > 0.01f) {
                confidence += 0.10f
            }

            if (vision.motionScore > 0.01f) {
                confidence += 0.05f
            }

            return confidence.coerceIn(
                0f,
                1f
            )
        }
    }
}
