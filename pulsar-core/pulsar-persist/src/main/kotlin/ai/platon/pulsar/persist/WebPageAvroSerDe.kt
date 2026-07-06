package ai.platon.pulsar.persist

import ai.platon.pulsar.persist.model.*
import org.apache.avro.Schema
import org.apache.avro.file.DataFileReader
import org.apache.avro.file.DataFileWriter
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.apache.avro.util.Utf8
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.ByteBuffer

/**
 * Avro serialization/deserialization for [WebPageRecord] using the existing
 * `webpage.avsc` schema from the classpath.
 *
 * Uses Avro's [GenericRecord] API with explicit field mapping to handle Kotlin
 * nullable types correctly. The schema is loaded once and cached.
 */
object WebPageAvroSerDe {

    private val logger = LoggerFactory.getLogger(WebPageAvroSerDe::class.java)

    /** The Avro schema loaded from the classpath resource. */
    val SCHEMA: Schema by lazy {
        val stream = WebPageAvroSerDe::class.java.getResourceAsStream("/avro/webpage.avsc")
            ?: error("Avro schema not found at /avro/webpage.avsc")
        Schema.Parser().parse(stream).also { schema ->
            logger.info("Avro schema loaded: {} fields", schema.fields.size)
        }
    }

    // Schema names for nested records
    private val protocolStatusSchema: Schema by lazy { SCHEMA.getField("protocolStatus").schema().types.find { it.name == "GProtocolStatus" }!! }
    private val parseStatusSchema: Schema by lazy { SCHEMA.getField("parseStatus").schema().types.find { it.name == "GParseStatus" }!! }
    private val hyperLinkSchema: Schema by lazy { SCHEMA.getField("liveLinks").schema().valueType.types.find { it.name == "GHypeLink" }!! }
    private val activeDOMStatusSchema: Schema by lazy { SCHEMA.getField("activeDOMStatus").schema().types.find { it.name == "GActiveDOMStatus" }!! }
    private val activeDOMStatSchema: Schema by lazy { SCHEMA.getField("activeDOMStatTrace").schema().valueType.types.find { it.name == "GActiveDOMStat" }!! }

    // --- Public API ---

    /**
     * Serializes a [WebPageRecord] to an Avro container file.
     *
     * @param record the record to serialize (content field should be null — content is stored separately)
     * @param file the target file (will be created or overwritten)
     */
    fun write(record: WebPageRecord, file: File) {
        val avroRecord = toAvro(record)
        val writer = GenericDatumWriter<GenericRecord>(SCHEMA)
        DataFileWriter<GenericRecord>(writer).use { fileWriter ->
            fileWriter.create(SCHEMA, file)
            fileWriter.append(avroRecord)
        }
    }

    /**
     * Deserializes a [WebPageRecord] from an Avro container file.
     *
     * @param file the Avro container file to read from
     * @return the deserialized record, or null if the file contains no records
     */
    fun read(file: File): WebPageRecord? {
        val reader = GenericDatumReader<GenericRecord>(SCHEMA)
        DataFileReader<GenericRecord>(file, reader).use { fileReader ->
            if (fileReader.hasNext()) {
                return fromAvro(fileReader.next())
            }
        }
        return null
    }

    // --- WebPageRecord -> GenericRecord ---

    fun toAvro(page: WebPageRecord): GenericRecord {
        val record = GenericData.Record(SCHEMA)
        record.put("baseUrl", page.baseUrl)
        record.put("createTime", page.createTime)
        record.put("distance", page.distance)
        record.put("fetchCount", page.fetchCount)
        record.put("fetchPriority", page.fetchPriority)
        record.put("fetchInterval", page.fetchInterval)
        putNullable(record, "zoneId", page.zoneId)
        putNullable(record, "params", page.params)
        putNullable(record, "batchId", page.batchId)
        putNullable(record, "resource", page.resource)
        record.put("crawlStatus", page.crawlStatus)
        putNullable(record, "browser", page.browser)
        putNullable(record, "proxy", page.proxy)
        record.put("prevFetchTime", page.prevFetchTime)
        record.put("prevCrawlTime1", page.prevCrawlTime1)
        record.put("fetchTime", page.fetchTime)
        record.put("fetchRetries", page.fetchRetries)
        putNullable(record, "reprUrl", page.reprUrl)
        record.put("prevModifiedTime", page.prevModifiedTime)
        record.put("modifiedTime", page.modifiedTime)
        // Protocol status
        val ps = page.protocolStatus
        if (ps != null) {
            record.put("protocolStatus", protocolStatusToAvro(ps))
        }
        putNullable(record, "encoding", page.encoding)
        putNullable(record, "contentType", page.contentType)
        // Content: always null in Avro (stored separately)
        record.put("content", null)
        record.put("contentLength", page.contentLength)
        record.put("lastContentLength", page.lastContentLength)
        record.put("aveContentLength", page.aveContentLength)
        record.put("persistedContentLength", page.persistedContentLength)
        putNullable(record, "referrer", page.referrer)
        putNullable(record, "htmlIntegrity", page.htmlIntegrity)
        putNullable(record, "anchor", page.anchor)
        record.put("anchorOrder", page.anchorOrder)
        // Parse status
        val parseSt = page.parseStatus
        if (parseSt != null) {
            record.put("parseStatus", parseStatusToAvro(parseSt))
        }
        putNullable(record, "pageTitle", page.pageTitle)
        putNullable(record, "pageText", page.pageText)
        putNullable(record, "contentTitle", page.contentTitle)
        putNullable(record, "contentText", page.contentText)
        record.put("contentTextLen", page.contentTextLen)
        putNullable(record, "pageCategory", page.pageCategory)
        record.put("contentModifiedTime", page.contentModifiedTime)
        record.put("prevContentModifiedTime", page.prevContentModifiedTime)
        record.put("contentPublishTime", page.contentPublishTime)
        record.put("prevContentPublishTime", page.prevContentPublishTime)
        record.put("refContentPublishTime", page.refContentPublishTime)
        record.put("prevRefContentPublishTime", page.prevRefContentPublishTime)
        record.put("pageModelUpdateTime", page.pageModelUpdateTime)
        putNullableBytes(record, "prevSignature", page.prevSignature)
        putNullableBytes(record, "signature", page.signature)
        record.put("contentScore", page.contentScore)
        record.put("score", page.score)
        putNullable(record, "sortScore", page.sortScore)
        // Maps
        record.put("pageCounters", toAvroIntMap(page.pageCounters))
        record.put("headers", toAvroStringMap(page.headers))
        record.put("links", GenericData.get().deepCopy(SCHEMA.getField("links").schema(), page.links.toList()))
        record.put("deadLinks", GenericData.get().deepCopy(SCHEMA.getField("deadLinks").schema(), page.deadLinks.toList()))
        record.put("liveLinks", toAvroHyperLinkMap(page.liveLinks))
        record.put("vividLinks", toAvroStringMap(page.vividLinks))
        record.put("inlinks", toAvroStringMap(page.inlinks))
        record.put("markers", toAvroStringMap(page.markers))
        record.put("metadata", toAvroBytesMap(page.metadata))
        // Active DOM
        val domStatus = page.activeDOMStatus
        if (domStatus != null) {
            record.put("activeDOMStatus", activeDOMStatusToAvro(domStatus))
        }
        val domStatTrace = page.activeDOMStatTrace
        if (domStatTrace != null) {
            record.put("activeDOMStatTrace", activeDOMStatTraceToAvro(domStatTrace))
        } else {
            record.put("activeDOMStatTrace", emptyMap<String, GenericRecord>())
        }
        // pageModel: not present in WebPageRecord, always null
        record.put("pageModel", null)

        return record
    }

    // --- GenericRecord -> WebPageRecord ---

    fun fromAvro(record: GenericRecord): WebPageRecord {
        val baseUrl = record.get("baseUrl")?.toString() ?: ""
        val page = WebPageRecord(baseUrl)
        page.createTime = longField(record, "createTime")
        page.distance = intField(record, "distance")
        page.fetchCount = intField(record, "fetchCount")
        page.fetchPriority = intField(record, "fetchPriority")
        page.fetchInterval = intField(record, "fetchInterval")
        page.zoneId = record.get("zoneId")?.toString()
        page.params = record.get("params")?.toString()
        page.batchId = record.get("batchId")?.toString()
        page.resource = record.get("resource") as? Int
        page.crawlStatus = intField(record, "crawlStatus")
        page.browser = record.get("browser")?.toString()
        page.proxy = record.get("proxy")?.toString()
        page.prevFetchTime = longField(record, "prevFetchTime")
        page.prevCrawlTime1 = longField(record, "prevCrawlTime1")
        page.fetchTime = longField(record, "fetchTime")
        page.fetchRetries = intField(record, "fetchRetries")
        page.reprUrl = record.get("reprUrl")?.toString()
        page.prevModifiedTime = longField(record, "prevModifiedTime")
        page.modifiedTime = longField(record, "modifiedTime")
        page.protocolStatus = protocolStatusFromAvro(record.get("protocolStatus"))
        page.encoding = record.get("encoding")?.toString()
        page.contentType = record.get("contentType")?.toString()
        // Content is null in Avro (stored separately)
        page.content = null
        page.contentLength = longField(record, "contentLength")
        page.lastContentLength = longField(record, "lastContentLength")
        page.aveContentLength = longField(record, "aveContentLength")
        page.persistedContentLength = longField(record, "persistedContentLength")
        page.referrer = record.get("referrer")?.toString()
        page.htmlIntegrity = record.get("htmlIntegrity")?.toString()
        page.anchor = record.get("anchor")?.toString()
        page.anchorOrder = intField(record, "anchorOrder")
        page.parseStatus = parseStatusFromAvro(record.get("parseStatus"))
        page.pageTitle = record.get("pageTitle")?.toString()
        page.pageText = record.get("pageText")?.toString()
        page.contentTitle = record.get("contentTitle")?.toString()
        page.contentText = record.get("contentText")?.toString()
        page.contentTextLen = intField(record, "contentTextLen")
        page.pageCategory = record.get("pageCategory")?.toString()
        page.contentModifiedTime = longField(record, "contentModifiedTime")
        page.prevContentModifiedTime = longField(record, "prevContentModifiedTime")
        page.contentPublishTime = longField(record, "contentPublishTime")
        page.prevContentPublishTime = longField(record, "prevContentPublishTime")
        page.refContentPublishTime = longField(record, "refContentPublishTime")
        page.prevRefContentPublishTime = longField(record, "prevRefContentPublishTime")
        page.pageModelUpdateTime = longField(record, "pageModelUpdateTime")
        page.prevSignature = record.get("prevSignature") as? ByteBuffer
        page.signature = record.get("signature") as? ByteBuffer
        page.contentScore = floatField(record, "contentScore")
        page.score = floatField(record, "score")
        page.sortScore = record.get("sortScore")?.toString()
        // Maps
        page.pageCounters.clear(); page.pageCounters.putAll(fromAvroIntMap(record.get("pageCounters")))
        page.headers.clear(); page.headers.putAll(fromAvroStringMap(record.get("headers")))
        page.links.clear(); fromAvroStringList(record.get("links"))?.let { page.links.addAll(it) }
        page.deadLinks.clear(); fromAvroStringList(record.get("deadLinks"))?.let { page.deadLinks.addAll(it) }
        page.liveLinks.clear(); page.liveLinks.putAll(fromAvroHyperLinkMap(record.get("liveLinks")))
        page.vividLinks.clear(); page.vividLinks.putAll(fromAvroStringMap(record.get("vividLinks")))
        page.inlinks.clear(); page.inlinks.putAll(fromAvroStringMap(record.get("inlinks")))
        page.markers.clear(); page.markers.putAll(fromAvroStringMap(record.get("markers")))
        page.metadata.clear(); page.metadata.putAll(fromAvroBytesMap(record.get("metadata")))
        // Active DOM
        page.activeDOMStatus = activeDOMStatusFromAvro(record.get("activeDOMStatus"))
        page.activeDOMStatTrace = activeDOMStatTraceFromAvro(record.get("activeDOMStatTrace"))

        return page
    }

    // --- Nested record helpers ---

    private fun protocolStatusToAvro(ps: ProtocolStatusRecord): GenericRecord {
        val rec = GenericData.Record(protocolStatusSchema)
        rec.put("majorCode", ps.majorCode)
        rec.put("minorCode", ps.minorCode)
        rec.put("args", toAvroStringMap(ps.args))
        return rec
    }

    private fun protocolStatusFromAvro(obj: Any?): ProtocolStatusRecord? {
        if (obj !is GenericRecord) return null
        return ProtocolStatusRecord(
            majorCode = obj.get("majorCode") as? Int ?: -1,
            minorCode = obj.get("minorCode") as? Int ?: -1,
            args = fromAvroStringMap(obj.get("args"))
        )
    }

    private fun parseStatusToAvro(ps: ParseStatusRecord): GenericRecord {
        val rec = GenericData.Record(parseStatusSchema)
        rec.put("majorCode", ps.majorCode)
        rec.put("minorCode", ps.minorCode)
        rec.put("args", toAvroStringMap(ps.args))
        return rec
    }

    private fun parseStatusFromAvro(obj: Any?): ParseStatusRecord? {
        if (obj !is GenericRecord) return null
        return ParseStatusRecord(
            majorCode = obj.get("majorCode") as? Int ?: -1,
            minorCode = obj.get("minorCode") as? Int ?: -1,
            args = fromAvroStringMap(obj.get("args"))
        )
    }

    private fun hyperLinkRecordToAvro(hl: HyperLinkRecord): GenericRecord {
        val rec = GenericData.Record(hyperLinkSchema)
        rec.put("url", hl.url)
        rec.put("anchor", hl.anchor)
        rec.put("order", hl.order)
        return rec
    }

    private fun hyperLinkRecordFromAvro(obj: Any?): HyperLinkRecord? {
        if (obj !is GenericRecord) return null
        return HyperLinkRecord(
            url = obj.get("url")?.toString() ?: "",
            anchor = obj.get("anchor")?.toString() ?: "",
            order = obj.get("order") as? Int ?: 0
        )
    }

    private fun activeDOMStatusToAvro(s: ActiveDOMStatus): GenericRecord {
        val rec = GenericData.Record(activeDOMStatusSchema)
        rec.put("n", s.n)
        rec.put("scroll", s.scroll)
        rec.put("st", s.st)
        rec.put("r", s.r)
        rec.put("idl", s.idl)
        rec.put("ec", s.ec)
        return rec
    }

    private fun activeDOMStatusFromAvro(obj: Any?): ActiveDOMStatus? {
        if (obj !is GenericRecord) return null
        return ActiveDOMStatus(
            n = obj.get("n") as? Int ?: 0,
            scroll = obj.get("scroll") as? Int ?: 0,
            st = obj.get("st")?.toString() ?: "",
            r = obj.get("r")?.toString() ?: "",
            idl = obj.get("idl")?.toString() ?: "",
            ec = obj.get("ec")?.toString() ?: ""
        )
    }

    private fun activeDOMStatToAvro(s: ActiveDOMStat): GenericRecord {
        val rec = GenericData.Record(activeDOMStatSchema)
        rec.put("ni", s.ni)
        rec.put("na", s.na)
        rec.put("nnm", s.nnm)
        rec.put("nst", s.nst)
        rec.put("w", s.w)
        rec.put("h", s.h)
        return rec
    }

    private fun activeDOMStatFromAvro(obj: Any?): ActiveDOMStat? {
        if (obj !is GenericRecord) return null
        return ActiveDOMStat(
            ni = obj.get("ni") as? Int ?: 0,
            na = obj.get("na") as? Int ?: 0,
            nnm = obj.get("nnm") as? Int ?: 0,
            nst = obj.get("nst") as? Int ?: 0,
            w = obj.get("w") as? Int ?: 0,
            h = obj.get("h") as? Int ?: 0
        )
    }

    /**
     * Maps ActiveDOMStatTrace to the Avro map<GActiveDOMStat> representation.
     * Uses well-known keys for the different stat slots.
     */
    private fun activeDOMStatTraceToAvro(trace: ActiveDOMStatTrace): Map<String, GenericRecord> {
        val map = linkedMapOf<String, GenericRecord>()
        trace.initStat?.let { map["initStat"] = activeDOMStatToAvro(it) }
        trace.lastStat?.let { map["lastStat"] = activeDOMStatToAvro(it) }
        trace.initD?.let { map["initD"] = activeDOMStatToAvro(it) }
        trace.lastD?.let { map["lastD"] = activeDOMStatToAvro(it) }
        if (trace.status != null) {
            // Store status fields as a synthetic stat record
            val statusRec = GenericData.Record(activeDOMStatSchema)
            statusRec.put("ni", trace.status.n)
            statusRec.put("na", trace.status.scroll)
            statusRec.put("nnm", 0)
            statusRec.put("nst", 0)
            statusRec.put("w", 0)
            statusRec.put("h", 0)
            map["status"] = statusRec
        }
        return map
    }

    private fun activeDOMStatTraceFromAvro(obj: Any?): ActiveDOMStatTrace? {
        if (obj !is Map<*, *>) return null
        val map = obj as Map<*, *>
        return ActiveDOMStatTrace(
            status = null, // Status cannot be reconstructed from Avro stat map alone; use activeDOMStatus field
            initStat = activeDOMStatFromAvro(map["initStat"]),
            lastStat = activeDOMStatFromAvro(map["lastStat"]),
            initD = activeDOMStatFromAvro(map["initD"]),
            lastD = activeDOMStatFromAvro(map["lastD"])
        )
    }

    // --- Map conversion helpers ---

    @Suppress("UNCHECKED_CAST")
    private fun toAvroStringMap(map: MutableMap<String, String>): Map<CharSequence, CharSequence> {
        val result = linkedMapOf<CharSequence, CharSequence>()
        for ((k, v) in map) {
            if (v != null) result[Utf8(k)] = Utf8(v)
        }
        return result
    }

    private fun fromAvroStringMap(obj: Any?): MutableMap<String, String> {
        if (obj !is Map<*, *>) return mutableMapOf()
        return (obj as Map<Any?, Any?>)
            .filter { it.key != null && it.value != null }
            .mapKeys { it.key.toString() }
            .mapValues { it.value.toString() }
            .toMutableMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun toAvroIntMap(map: MutableMap<String, Int>): Map<CharSequence, Any?> {
        val result = linkedMapOf<CharSequence, Any?>()
        for ((k, v) in map) {
            result[Utf8(k)] = v
        }
        return result
    }

    private fun fromAvroIntMap(obj: Any?): MutableMap<String, Int> {
        if (obj !is Map<*, *>) return mutableMapOf()
        return (obj as Map<Any?, Any?>)
            .filter { it.key != null && it.value != null }
            .mapKeys { it.key.toString() }
            .mapValues { (it.value as? Number)?.toInt() ?: 0 }
            .toMutableMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun toAvroBytesMap(map: MutableMap<String, ByteBuffer>): Map<CharSequence, Any?> {
        val result = linkedMapOf<CharSequence, Any?>()
        for ((k, v) in map) {
            result[Utf8(k)] = v  // ByteBuffer or null (Avro schema allows null for map values)
        }
        return result
    }

    private fun fromAvroBytesMap(obj: Any?): MutableMap<String, ByteBuffer> {
        if (obj !is Map<*, *>) return mutableMapOf()
        return (obj as Map<Any?, Any?>)
            .filter { it.key != null }
            .mapKeys { it.key.toString() }
            .mapValues { (it.value as? ByteBuffer) }
            .filterValues { it != null }
            .mapValues { it.value!! }
            .toMutableMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun toAvroHyperLinkMap(map: MutableMap<String, HyperLinkRecord>): Map<CharSequence, GenericRecord> {
        val result = linkedMapOf<CharSequence, GenericRecord>()
        for ((k, v) in map) {
            result[Utf8(k)] = hyperLinkRecordToAvro(v)
        }
        return result
    }

    private fun fromAvroHyperLinkMap(obj: Any?): MutableMap<String, HyperLinkRecord> {
        if (obj !is Map<*, *>) return mutableMapOf()
        return (obj as Map<Any?, Any?>)
            .filter { it.key != null && it.value != null }
            .mapKeys { it.key.toString() }
            .mapValues { hyperLinkRecordFromAvro(it.value) }
            .filterValues { it != null }
            .mapValues { it.value!! }
            .toMutableMap()
    }

    private fun fromAvroStringList(obj: Any?): List<String>? {
        if (obj !is List<*>) return null
        return obj.filterNotNull().map { it.toString() }
    }

    // --- Field access helpers ---

    private fun longField(record: GenericRecord, name: String): Long {
        return record.get(name) as? Long ?: 0L
    }

    private fun intField(record: GenericRecord, name: String): Int {
        return record.get(name) as? Int ?: 0
    }

    private fun floatField(record: GenericRecord, name: String): Float {
        return record.get(name) as? Float ?: 0f
    }

    private fun putNullable(record: GenericRecord, name: String, value: Any?) {
        record.put(name, value)
    }

    private fun putNullableBytes(record: GenericRecord, name: String, value: ByteBuffer?) {
        record.put(name, value)
    }
}
