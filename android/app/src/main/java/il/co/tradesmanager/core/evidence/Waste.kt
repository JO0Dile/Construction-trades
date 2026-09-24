package il.co.tradesmanager.core.evidence

/**
 * Where a site's waste went.
 *
 * Every skip and every lorry of rubble leaves the site for somewhere, and at
 * the end of a job the question is whether anybody can show where. A local
 * authority can ask for proof that construction waste went to a licensed
 * facility, a client with a green-building target asks how much was kept out
 * of landfill, and the answer to both is a stack of weighbridge tickets that
 * is usually in the glovebox of a lorry that has gone.
 *
 * So each load is recorded when it leaves, with its ticket -- the number, a
 * photograph of it, or both. A load with neither is recorded anyway, because
 * it happened, and it is counted as unproven until one is added: the handover
 * pack says how many there are.
 *
 * None of this is offered as a statement of what any authority requires on a
 * particular job. It keeps the evidence for whoever asks.
 */
object Waste {

    /**
     * What the load was.
     *
     * Deliberately no "recyclable" flag on these. Whether a load was kept out
     * of landfill depends on where it went, not what it was -- concrete
     * crushed for aggregate and concrete tipped are the same stream -- so the
     * diversion figure is taken from [Destination] and nothing else.
     */
    enum class Stream {
        /** Concrete, block, brick, tile: crushed and reused as aggregate. */
        CONCRETE_AND_MASONRY,
        WOOD,
        METAL,
        PLASTIC,
        CARDBOARD_AND_PAPER,

        /** Excavated earth. */
        SOIL,

        /** Everything thrown in one skip together. Sorted, if at all, somewhere else. */
        MIXED,

        /**
         * Asbestos, oils, solvents, paint, treated timber. Handled under its
         * own rules, and never without a ticket -- see [Refusal.HAZARDOUS_WITHOUT_TICKET].
         */
        HAZARDOUS,
    }

    /** Where it went. */
    enum class Destination(val keptOutOfLandfill: Boolean) {
        /** A licensed recycling or crushing facility. */
        RECYCLING(keptOutOfLandfill = true),

        /** Reused on this site or another: backfill, hardcore, formwork again. */
        REUSED(keptOutOfLandfill = true),

        /**
         * A transfer station, which sorts it on. Not counted as kept out of
         * landfill, because this record cannot see what the station did with
         * it and claiming otherwise would be claiming for somebody else.
         */
        TRANSFER_STATION(keptOutOfLandfill = false),

        LANDFILL(keptOutOfLandfill = false),
    }

    /**
     * What the quantity is in. Named for what it is rather than `Unit`, which
     * would shadow Kotlin's own inside this object.
     */
    enum class Measurement { TONNES, CUBIC_METRES }

    enum class Refusal {
        /** Nothing, or less than nothing. */
        NO_QUANTITY,

        /** Where it went was not said. That is the whole of the record. */
        NO_DESTINATION,

        /**
         * Hazardous waste with no ticket number. The one stream where "we'll
         * find the paperwork later" is not acceptable: the ticket is the
         * record that it went somewhere licensed to take it.
         */
        HAZARDOUS_WITHOUT_TICKET,
    }

    /** One load, as the rules see it. */
    data class Load(
        val stream: Stream,
        val quantity: Double,
        val unit: Measurement,
        val destination: Destination,
        val facility: String,
        val ticketNumber: String?,
        val ticketPhotos: Int,
    ) {
        /** A ticket number or a photograph of the ticket: something that shows where it went. */
        val proven: Boolean get() = !ticketNumber.isNullOrBlank() || ticketPhotos > 0
    }

    /** Why a load cannot be recorded, or null when it can. */
    fun refusal(
        stream: Stream,
        quantity: Double,
        facility: String,
        ticketNumber: String?,
    ): Refusal? = when {
        !quantity.isFinite() || quantity <= 0.0 -> Refusal.NO_QUANTITY
        facility.isBlank() -> Refusal.NO_DESTINATION
        stream == Stream.HAZARDOUS && ticketNumber.isNullOrBlank() -> Refusal.HAZARDOUS_WITHOUT_TICKET
        else -> null
    }

    /**
     * A job's waste, summed.
     *
     * Kept per unit and never added across units: a tonne of concrete and a
     * cubic metre of mixed skip waste are not the same amount of anything,
     * and a total that added them would be a number with no meaning printed
     * as though it had one.
     */
    data class Totals(
        val unit: Measurement,
        val total: Double,
        val keptOutOfLandfill: Double,
        val unproven: Int,
    ) {
        /**
         * The share kept out of landfill, or null when there is nothing to
         * share. Not zero: a job with no waste recorded has not diverted
         * nothing, it has recorded nothing.
         */
        val diversionRate: Double? get() = if (total > 0.0) keptOutOfLandfill / total else null
    }

    fun totals(loads: List<Load>): List<Totals> =
        Measurement.entries.mapNotNull { unit ->
            val mine = loads.filter { it.unit == unit }
            if (mine.isEmpty()) {
                null
            } else {
                Totals(
                    unit = unit,
                    total = mine.sumOf { it.quantity },
                    keptOutOfLandfill = mine
                        .filter { it.destination.keptOutOfLandfill }
                        .sumOf { it.quantity },
                    unproven = mine.count { !it.proven },
                )
            }
        }

    /** Loads nobody can yet show went anywhere: no ticket number and no photograph. */
    fun unproven(loads: List<Load>): Int = loads.count { !it.proven }
}
