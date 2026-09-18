package dev.octoshrimpy.quik.util

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class EmojiPatternStrings(
    @Json(name = "emoji_reaction_ios_generic_added") val iosGenericAdded: String? = null,
    @Json(name = "emoji_reaction_ios_generic_removed") val iosGenericRemoved: String? = null,

    @Json(name = "emoji_reaction_ios_heart_added") val iosHeartAdded: String? = null,
    @Json(name = "emoji_reaction_ios_heart_removed") val iosHeartRemoved: String? = null,

    @Json(name = "emoji_reaction_ios_like_added") val iosLikeAdded: String? = null,
    @Json(name = "emoji_reaction_ios_like_removed") val iosLikeRemoved: String? = null,

    @Json(name = "emoji_reaction_ios_dislike_added") val iosDislikeAdded: String? = null,
    @Json(name = "emoji_reaction_ios_dislike_removed") val iosDislikeRemoved: String? = null,

    @Json(name = "emoji_reaction_ios_laugh_added") val iosLaughAdded: String? = null,
    @Json(name = "emoji_reaction_ios_laugh_removed") val iosLaughRemoved: String? = null,

    @Json(name = "emoji_reaction_ios_exclamation_added") val iosExclamationAdded: String? = null,
    @Json(name = "emoji_reaction_ios_exclamation_removed") val iosExclamationRemoved: String? = null,

    @Json(name = "emoji_reaction_ios_question_mark_added") val iosQuestionMarkAdded: String? = null,
    @Json(name = "emoji_reaction_ios_question_mark_removed") val iosQuestionMarkRemoved: String? = null,

    // Forward templates used to *generate* outgoing iOS-readable reactions. "%1$s" is the emoji
    // (generic template only) and the last "%s" is the target message text. When absent for a
    // locale, English defaults are used.
    @Json(name = "emoji_reaction_ios_generic_added_template") val iosGenericAddedTemplate: String? = null,
    @Json(name = "emoji_reaction_ios_generic_removed_template") val iosGenericRemovedTemplate: String? = null,
    @Json(name = "emoji_reaction_ios_heart_added_template") val iosHeartAddedTemplate: String? = null,
    @Json(name = "emoji_reaction_ios_heart_removed_template") val iosHeartRemovedTemplate: String? = null,
    @Json(name = "emoji_reaction_ios_like_added_template") val iosLikeAddedTemplate: String? = null,
    @Json(name = "emoji_reaction_ios_like_removed_template") val iosLikeRemovedTemplate: String? = null,
    @Json(name = "emoji_reaction_ios_dislike_added_template") val iosDislikeAddedTemplate: String? = null,
    @Json(name = "emoji_reaction_ios_dislike_removed_template") val iosDislikeRemovedTemplate: String? = null,
    @Json(name = "emoji_reaction_ios_laugh_added_template") val iosLaughAddedTemplate: String? = null,
    @Json(name = "emoji_reaction_ios_laugh_removed_template") val iosLaughRemovedTemplate: String? = null,
    @Json(name = "emoji_reaction_ios_exclamation_added_template") val iosExclamationAddedTemplate: String? = null,
    @Json(name = "emoji_reaction_ios_exclamation_removed_template") val iosExclamationRemovedTemplate: String? = null,
    @Json(name = "emoji_reaction_ios_question_mark_added_template") val iosQuestionMarkAddedTemplate: String? = null,
    @Json(name = "emoji_reaction_ios_question_mark_removed_template") val iosQuestionMarkRemovedTemplate: String? = null,
)
