{
  import groovy.json.JsonSlurper
  import groovy.json.JsonOutput

  def norm = { it.replaceTrailingBrackets()
                 // .upperInitial().lowerTrail()
                 .replaceAll(/[`´‘’ʻ""“”]/, "'")
                 .replaceAll(/[:|]/, " - ")
                 // .replaceAll(/[:]/, "\u2236") // "∶" Ratio symbol
                 // .replaceAll(/[:]/, "\uFF1A") // "：" Fullwidth Colon
                 // .replaceAll(/[:]/, "\uFE55") // "﹕" Small Colon
                 // .replaceAll("/", "\u29F8") // "⧸" Big Solidus
                 // .replaceAll("/", "\u2215") // "∕" Division Slash
                 // .replaceAll("/", "\u2044") // "⁄" Fraction Slash
                 // .replaceAll(/[?]/, "\uFF1F") // "？" Fullwidth Question Mark
                 .replaceAll(/[?]/, "\uFE56") // "﹖" Small Question Mark
                 .replaceAll(/[\*]/, "\u204E") // "⁎" low asterisk
                 .replaceAll(/[*\s]+/, " ")
                 .replaceAll(/\b[IiVvXx]+\b/, { it.upper() })
                 .replaceAll(/\b[0-9](?i:th|nd|rd)\b/, { it.lower() }) }

  def isLatin = { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD)
                                      .replaceAll(/\p{InCombiningDiacriticalMarks}+/, "") ==~ /^\p{InBasicLatin}+$/ }

  def translJap = {
    /* rate limited to 100 per day I believe, please be careful */
    def url = new URL("https://api.kuroshiro.org/convert")
    def requestHeaders = [:]
    def postBody = [:]
      postBody.str = it
      postBody.to = "romaji"
      postBody.mode = "spaced"
      postBody.romajiSystem = "hepburn"
    def postResponse = url.post(JsonOutput.toJson(postBody).getBytes("UTF-8"), "application/json", requestHeaders)
    def json = new JsonSlurper().parseText(postResponse.text)
    return json.result
  }

  def transl = {
    (languages.first().iso_639_2B == "jpn") ? translJap(it) : it.transliterate("Any-Latin; NFD; NFC; Title") }

allOf
  { if ((media.OverallBitRate.toInteger() / 1000 < 3000 && vf.minus("p").toInteger() >= 720)
       || vf.minus("p").toInteger() < 720) {
      return "LQ_Movies"
    } else {
      return "Movies"
    }
  }
  // Movies directory
  { def film_directors = info.directors.sort().join(", ")
    n.colon("\u2236 ") + " ($y) [$film_directors]" }
  // File name
  { allOf
    { isLatin(primaryTitle) ? primaryTitle.colon("\u2236 ") : transl(primaryTitle).colon("\u2236 ") }
    {" ($y)"}
    // tags + a few more variants
    { def last = n.tokenize(" ").last()
      /* def _tags = (tags != null) ? tags : null */
      def _tags = any{tags}{null}
      if (_tags) {
        _tags.removeIf { it ==~ /(?i:imax)/ }
      }

      specials = allOf
                  { _tags }
                  { fn.after(/(?i:$last)/).findAll(/(?i)(alternate|first)[ ._-]cut|limited|hybrid/)
                    *.upperInitial()*.lowerTrail()*.replaceAll(/[._-]/, " ") }
                  { fn.after(/(?i:$last)/).findAll(/(?i)imax.?(edition|version)?/)
                    *.upperInitial()*.lowerTrail()*.replaceAll(/[._-]/, " ")
                    *.replaceAll(/(?i:imax)/, "IMAX") }
                  { if (!!(fn.after(/(?i:$last)/) =~ /\WDC\W/)) "Directors Cut" }
                  { fn.after(/(?i:$last)/).match(/remaster/).upperInitial().lowerTrail() }
                  .flatten().sort()
      if (specials.size() > 0) {
        specials.removeIf{ a ->
          _tags.any{ b ->
            a != b && (b.startsWith(a) || b.endsWith(a)) } }
        specials.unique().join(", ").replaceAll(/^/, " - ") } }
    {" PT $pi"}
    {" ["}
    { allOf
      { // Video
        // net.filebot.media.VideoFormat.DEFAULT_GROUPS.guessFormat(dim[0], dim[1])
        allOf
          { vf }
          { vc }
          {
            def _HDRMap = [
              "HDR10": "HDR10",
              "SMPTE ST 2086": "HDR10",
              "SMPTE ST 2094 App 3": "Advanced HDR",
              "SMPTE ST 2094 App 4": "HDR10+",
              "Dolby Vision / SMPTE ST 2086": "Dolby Vision",
              "Dolby Vision / HDR10": "Dolby Vision",
              "ETSI TS 103 433": "SL-HDR1",
              "SL-HDR1": "SL-HDR1", // , Version 1.0, Parameter-based, constant
                                    // , Version 1.0, Parameter-based, non-constant
              "SL-HDR2": "SL-HDR2", // , Version 0.0, Parameter-based
              "SL-HDR3": "SL-HDR3",
              "Technicolor Advanced HDR": "Technicolor Advanced HDR",
            ]
            def vid = video.first()
            if (bitdepth > 8) {
              switch (vid) {
                case { vid =~ /\bHDR_Format_Commercial/ }:
                  vid["HDR_Format_Commercial"]
                  break
                case { vid =~ /\bHDR_/ }:
                  _HDR = any
                     { vid["HDR_Format"] }
                     { vid["HDR_Format/String"] }
                     // { vid["HDR_Format/String"] }
                     // { vid["HDR_Format_Compatibility"] }
                     // following for both HDR10+ (misses compatibility) and Dolby Vision
                     // { vid["HDR_Format_Version"] }
                     // following only for Dolby Vision
                     // { vid["HDR_Format_Profile"] }
                     // { vid["HDR_Format_Level"] }
                     // { vid["HDR_Format_Settings"] }

                  hdr_out = _HDRMap.get(_HDR, _HDR)
                  if ( hdr_out.findMatch(/vision/) ) {
                    dv_info = allOf
                      { "P" }
                      { vid["HDR_Format_Profile"].match(/[dh][ve][hvca][e13v]\.\d(\d)/) }
                      { "." + vid["HDR_Format_Compatibility"].match(/HDR10|SDR/).replace("HDR10", "1").replace("SDR", "2") }
                    .join()
                    hdr_out = "$hdr_out $dv_info"
                  }
                  hdr_out
                  break
                case { it["transfer_characteristics"].findMatch(/HLG/) && it["colour_primaries"] == "BT.2020" }:
                  "HLG10" // HLG
                  break
                case { it["transfer_characteristics"] == "PQ" && it["colour_primaries"] == "BT.2020" }:
                  "HDR10" // PQ10 or HDR
                  break
                default:
                  "$bitdepth-bit"
                break
              }
            }
          }
        .join(" ")
      }
      {
        /* def audioClean = { if (it != null) it.replaceAll(/[\p{Pd}\p{Space}]/, " ").replaceAll(/\p{Space}{2,}/, " ") }
        def mCFP = [
          "AC3" : "AC3",
          "AC3+" : "E-AC3",
          "TrueHD" : "TrueHD",
          "TrueHD TrueHD+Atmos / TrueHD" : "TrueHD ATMOS",
          "DTS" : "DTS",
          "DTS HD HRA / Core" : "DTS-HD HRA",
          "DTS HD MA / Core" : "DTS-HD MA",
          "DTS HD X / MA / Core" : "DTS-X",
          "FLAC" : "FLAC",
          "PCM" : "PCM",
          "AC3+ E AC 3+Atmos / E AC 3": "E-AC3+Atmos",
          "AAC LC LC" : "AAC-LC",
          "AAC LC SBR HE AAC LC": "HE-AAC"
        ] */

        // audio map, some of these are probably not needed anymore
        def mCFP = [
          "FLAC": "FLAC",
          "PCM": "PCM",
          "MPEG Audio Layer 3": "MP3",
          "AAC LC": "AAC LC",
          "AAC LC SBR": "HE-AAC", // HE-AACv1
          "AAC LC SBR PS": "HE-AACv2",
          "AC-3 Dep": "E-AC-3+Dep",
          "AC-3 Blu-ray Disc Dep": "E-AC-3+Dep",
          "E-AC-3 Blu-ray Disc Dep": "E-AC-3+Dep",
          "E-AC-3 Dep": "E-AC-3+Dep",
          "E-AC-3 JOC": "E-AC-3 JOC",
          "DTS XBR": "DTS-HD HRA", // needs review
          "DTS ES": "DTS-ES Matrix",
          "DTS ES XBR": "DTS-HD HRA",
          "DTS ES XXCH XBR": "DTS-HD HRA", // needs review
          "DTS ES XXCH": "DTS-ES Discrete",
          "DTS ES XXCH XLL": "DTS-HD MA", // needs review
          "DTS XLL": "DTS-HD MA",
          /* "DTS XLL X": "DTS\u02D0X", // IPA triangular colon */
          "DTS XLL X": "DTS-X",
          "MLP FBA": "TrueHD",
          "MLP FBA 16-ch": "TrueHD",
          "DTS 96/24": "DTS 96-24", // needs review
        ]

        audio.collect { au ->
          /* Format seems to be consistently defined and identical to Format/String
             Format_Profile and Format_AdditionalFeatures instead
             seem to be usually mutually exclusive
             Format_Commercial (and _If_Any variant) seem to be defined
             mainly for Dolby/DTS formats */
          String _ac = any
                        { allOf
                            { any{ au["Format/String"] }{ au["Format"] } }
                            { au["Format_Profile"] }
                            { au["Format_AdditionalFeatures"] }
                          .collect{ it.tokenize() }.flatten().unique().join(" ") }
                        { au["Format_Commercial"] }
          /* original _aco_ binding uses "Codec_Profile", "Format_Profile", "Format_Commercial" */
          String _aco = any{ au["Codec_Profile"] }{ au["Format_Profile"] }{ au["Format_Commercial"] }
          /* def atmos = (_aco =~ /(?i:atmos)/) ? "Atmos" : null */
          def _fAtmos = any{ audio.FormatCommercial =~ /(?i)atmos/ }{ false }
          def _oAtmos = any{ audio.NumberOfDynamicObjects }{ false }
          String isAtmos = (_fAtmos || _oAtmos) ? "Atmos" : null
          /* _channels_ uses "ChannelPositions/String2", "Channel(s)_Original", "Channel(s)"
             compared to _af_ which uses "Channel(s)_Original", "Channel(s)"
             local _channels uses the same variables as {channels} but calculates
             the result for each audio stream */
          String    _channels = any
                                  { au["ChannelPositions/String2"] }
                                  { au["Channel(s)_Original"] }
                                  { au["Channel(s)"] }
          String    _ch
          /* _channels can contain no numbers */
          Object    splitCh = _channels =~ /^(?i)object.based$/ ? "Object Based" :
                              _channels.tokenize("\\/\\.")
                              /* the below may be needed for 3/2/0.2.1/3/2/0.1 files */
                              // _channels.tokenize("\\/").take(3)*.tokenize("\\.")
                              //          .flatten()*.toInteger()

          String    chSimple = any{ au["Channel(s)"] }{ au["Channel(s)/String"].replaceAll("channels", "") }

          switch (splitCh) {
            case { it instanceof String }:
              _ch = allOf{ splitCh }{ chSimple + "ch" }.join(" ")
              break

            case { it.size > 4 }:
              def wide = splitCh.takeRight(1)
              Double main = splitCh.take(4)*.toDouble().inject(0, { a, b -> a + b })
              Double sub = Double.parseDouble("0." + wide.last())
              _ch = (main + sub).toBigDecimal().setScale(1, java.math.RoundingMode.HALF_UP).toString()
              break

            case { it.size > 1 }:
              /* original logic is _mostly_ unchanged if format is like 3/2/0.1 */
              Double sub = Double.parseDouble(splitCh.takeRight(2).join("."))
              _ch = splitCh.take(2)*.toDouble().plus(sub).inject(0, { a, b -> a + b })
                           .toBigDecimal().setScale(1, java.math.RoundingMode.HALF_UP).toString()
              break

            default:
              _ch = splitCh.first().toDouble()
          }

          /* UNUSED - possible fix for mistakes in ChannelPositions/String2 */
          String _channelPositions = any{au["ChannelPositions"]}{null}
          String channelParse
          if ( chSimple.toInteger() != _ch.tokenize(".")*.toInteger().sum() &&
               _channelPositions != null ) {
            List   channelsPos = _channelPositions.tokenize(",")
            String mainFix = channelsPos.take(3).inject(0) { acc, p ->
              Integer parsedCh = p.tokenize(":").takeRight(1).first().trim().tokenize(" ").size()
              acc + parsedCh
            }
            String subFix = channelsPos.takeRight(1).first().trim().tokenize(" ").size()
            channelParse = "${mainFix}.${subFix}"
          }
          /* UNUSED */

          def stream = allOf
            { allOf{ _ch }{ au["NumberOfDynamicObjects"] + "obj" }.join("+") }
            { allOf{ mCFP.get(_ac, _ac) }{isAtmos/* atmos */}.join("+") }
            /* { allOf{ mCFP.get(combined, _aco) }{atmos}.join("+") } /* bit risky keeping _aco as default */
            { def _lang = any{ au["Language"] }{ video.first()["Language"] }
              net.filebot.Language.findLanguage(_lang).ISO3.upperInitial() }
            /* _cf_ not being used > "Codec/Extensions", "Format" */
          def ret = [:]
          /* this is done to retain stream order */
          ret.id = any{ au["StreamKindId"] }{ au["StreamKindPos"] }{ au["ID"] }
          ret.data = stream
          return ret
        }.toSorted{ it.id }.collect{ it.data }*.join(" ").join(", ") }
        /* .sort{ a, b -> a.first() <=> b.first() }.reverse() */
      /* logo-free release source finder + source */
      { def fileURL = new URL("file:///scripts/websources.txt")
        def file = new File(fileURL.toURI())
        def websources = file.exists() ? lines(file).join("|") : null
        def isWeb = (source ==~ /WEB.*/)
        // def isWeb = source.matches(/WEB.*/) don't know which one is preferrable
        String lfr
        if (isWeb) {
          lfr = any{ fn.match(/($websources)\.(?i)WEB/) }
                   { if (fn.matches(/(?<=\d{3}[p].)WEB|WEB(?=.[hx]\d{3})/)) 'WEB-DL' }
                   { null }
        }
        allOf
          { def yrange = (y-1)..(y+1)
            fn.find(/([0-9]{4}).([A-Z]{3}).1080p/) { match, year, country ->
            	if (match &&
                  yrange.contains(year.toInteger()) &&
                  Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA3).contains(country))
              country
            }
          }
          { fn.match(/(?i)(UHD).$source/).upper() }
          { lfr }
          {
            def replacements = [
              'Blu-Ray': 'BluRay',
              'Blu-ray': 'BluRay',
              'BD': 'BluRay'
            ]
            source.replace(replacements)
          }
        .join(".") }
      .join(" - ") }
    {"]"}
    { def ed = fn.findAll(/(?i)repack|proper|rerip/)*.upper().join(".")
      // def ed = allOf{fn.match(/repack|proper/)}{f.dir.path.match(/repack|proper/)}*.upper().join(".")
      if (ed) { ".$ed" } }
    /* { any{"-$group"}{"-" + fn.match(/(?:(?<=[-])\w+$)|(?:^\w+(?=[-]))/)} } */
    {"-$group"}
    {subt}
    .join("") }
  .join("/") }
