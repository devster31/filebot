{ import net.filebot.Language
  import java.math.RoundingMode
  import groovy.json.JsonSlurper
  import groovy.json.JsonOutput

  def norm = { it.replaceTrailingBrackets()
                 // .upperInitial().lowerTrail()
                 .replaceAll(/[`´‘’ʻ""“”]/, "'")
                 .replaceAll(/[:|]/, " - ")
                 // .replaceAll(/[:]/, "\uFF1A")
                 // .replaceAll(/[:]/, "\u2236") // ratio
                 .replaceAll(/[?]/, "\uFE56")
                 .replaceAll(/[*\s]+/, " ")
                 .replaceAll(/\b[IiVvXx]+\b/, { it.upper() })
                 .replaceAll(/\b[0-9](?i:th|nd|rd)\b/, { it.lower() }) }

  def isLatin = { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD)
                                      .replaceAll(/\p{InCombiningDiacriticalMarks}+/, "") ==~ /^\p{InBasicLatin}+$/ }

  def translJap = {
    // rate limited to 100 per day I believe, please be careful
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
  // { if (vf.minus("p").toInteger() < 1080 || ((media.OverallBitRate.toInteger() / 1000 < 3000) && vf.minus("p").toInteger() >= 720)) { } }
  { if ((media.OverallBitRate.toInteger() / 1000 < 3000 && vf.minus("p").toInteger() >= 720) || vf.minus("p").toInteger() < 720) {
      return "LQ_Movies"
    } else {
      return "Movies"
    } }
  // Movies directory
  { def film_directors = info.directors.sort().join(", ")
    n.colon(" - ") + " ($y) [$film_directors]" }
  // File name
  { allOf
    { isLatin(primaryTitle) ? primaryTitle.colon(" - ") : transl(primaryTitle).colon(" - ") }
    {" ($y)"}
    // tags + a few more variants
    { def last = n.tokenize(" ").last()
      def t = tags
      t.removeIf { it ==~ /(?i:imax)/ }
      specials = { allOf
                    { t }
                    { fn.after(/(?i:$last)/).findAll(/(?i:alternate[ ._-]cut|limited)/)
                      *.upperInitial()*.lowerTrail()*.replaceAll(/[._-]/, " ") }
                    { fn.after(/(?i:$last)/).findAll(/(?i:imax).(?i:edition|version)?/)
                      *.upperInitial()*.lowerTrail()*.replaceAll(/[._-]/, " ")
                      *.replaceAll(/(?i:imax)/, "IMAX") }
                    .flatten().sort() }
      specials().size() > 0 ? specials().join(", ").replaceAll(/^/, " - ") : "" }
    {" PT $pi"}
    {" ["}
    { allOf
      // Video stream
      { allOf{vf}{vc}.join(" ") }
      { /* def audioClean = { if (it != null) it.replaceAll(/[\p{Pd}\p{Space}]/, " ").replaceAll(/\p{Space}{2,}/, " ") }
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
          "FLAC" : "FLAC",
          "PCM" : "PCM",
          "MPEG Audio Layer 3": "MP3",
          "AAC LC": "AAC LC",
          "AAC LC SBR": "HE-AAC", // HE-AACv1
          "AAC LC SBR PS": "HE-AACv2",
          "E-AC-3 JOC": "E-AC-3",
          "DTS ES": "DTS-ES Matrix",
          "DTS ES XXCH": "DTS-ES Discrete",
          "DTS XLL": "DTS-HD MA",
          /* "DTS XLL X": "DTS\u02D0X", // IPA triangular colon */
          "DTS XLL X": "DTS-X",
          "DTS XBR": "DTS-HR",
          "MLP FBA": "TrueHD",
          "MLP FBA 16-ch": "TrueHD"
        ]
        audio.collect { au ->
          /* Format seems to be consistently defined and identical to Format/String
             Format_Profile and Format_AdditionalFeatures instead
             seem to be usually mutually exclusive
             Format_Commercial (and _If_Any variant) seem to be defined
             mainly for Dolby/DTS formats */
          def _ac = any{allOf{ au["Format"] }{ au["Format_Profile"] }{ au["Format_AdditionalFeatures"] }}{ au["Format_Commercial"] }.join(" ")
          def _aco = any{ au["Codec_Profile"] }{ au["Format_Profile"] }{ au["Format_Commercial"] } // _aco_ uses "Codec_Profile", "Format_Profile", "Format_Commercial"
          /* def atmos = (_aco =~ /(?i:atmos)/) ? "Atmos" : null */
          def isAtmos = {
            def _fAtmos = any{audio.FormatCommercial =~ /(?i)atmos/}{false}
            def _oAtmos = any{audio.NumberOfDynamicObjects}{false}
            if (_fAtmos || _oAtmos) { return "Atmos" }
          }
          /* _channels_ uses "ChannelPositions/String2", "Channel(s)_Original", "Channel(s)"
               compared to _af_ which uses "Channel(s)_Original", "Channel(s)"
             local _channels uses the same variables as {channels} but calculates
             the result for each audio stream */
          def _channels = any{ au["ChannelPositions/String2"] }{ au["Channel(s)_Original"] }{ au["Channel(s)"] }
          /* _channels can contain no numbers */
          def ch = _channels =~ /^(?i)object.based$/ ? 'Object Based' :
                   _channels.tokenize("\\/").take(3)*.toDouble()
                            .inject(0, { a, b -> a + b }).findAll { it > 0 }.max()
                            .toBigDecimal().setScale(1, RoundingMode.HALF_UP).toString()
          def stream = allOf
            { allOf{ ch }{ au["NumberOfDynamicObjects"] + "obj" }.join("+") }
            { allOf{ mCFP.get(_ac, _ac) }{isAtmos/* atmos */}.join("+") }
            /* { allOf{ mCFP.get(combined, _aco) }{atmos}.join("+") } /* bit risky keeping _aco as default */
            { Language.findLanguage(au["Language"]).ISO3.upperInitial() }
            /* _cf_ not being used > "Codec/Extensions", "Format" */
          def ret = [:]
          /* this is done to retain stream order */
          ret.id = any{ au["StreamKindId"] }{ au["StreamKindPos"] }{ au["ID"] }
          ret.data = stream
          return ret
        }.toSorted{ it.id }.collect{ it.data }*.join(" ").join(", ") }
      /* .sort{ a, b -> a.first() <=> b.first() }.reverse() */
      /* source */
      { // logo-free release source finder
        def file = new File("/scripts/websources.txt")
        def websources = file.exists() ? readLines(file).join("|") : null
        def isWeb = (source ==~ /WEB.*/)
        // def isWeb = source.matches(/WEB.*/) don't know which one is preferrable
        def lfr = { if (isWeb) fn.match(/($websources)\.(?i)WEB/) }
        return allOf{fn.match(/(?i)(UHD).$source/).upper()}{lfr}{source}.join(".") }
      .join(" - ") }
    {"]"}
    { def ed = fn.findAll(/(?i:repack|proper)/)*.upper().join()
      if (ed) { return "." + ed } }
    /* { any{"-$group"}{"-" + fn.match(/(?:(?<=[-])\w+$)|(?:^\w+(?=[-]))/)} } */
    {"-$group"}
    {subt}
    .join("") }
  .join("/") }
