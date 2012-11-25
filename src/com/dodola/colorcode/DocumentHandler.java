package com.dodola.colorcode;

import java.util.HashMap;
import java.util.Map;

import android.text.TextUtils;

public class DocumentHandler {
	private String mimeType = "";

	private final String[][] ExtentsionMap = {
			{ "h|hpp|hxx|cpp|cxx|cc", "cpp" }, { "c", "c" },
			{ "cs", "csharp" }, { "css", "css" }, { "diff|patch", "diff" },
			{ "as", "flex" }, { "html|htm|shtml|shtm|xhtml", "html" },
			{ "java", "java" }, { "properties", "properties" },
			{ "js", "javascript" }, { "tex", "latex" }, { "ldap", "ldap" },
			{ "log", "log" }, { "lsm", "lsm" }, { "m4", "m4" },
			{ "mk|makefile", "makefile" },
			{ "ml|mli|cmo|cmx|cma|cmxa", "caml" }, { "sql", "sql" },
			{ "pas|inc", "pascal" }, { "pl|pm|plx", "perl" },
			{ "php|php3|phtml", "php" }, { "prolog", "prolog" },
			{ "py|pyw", "python" }, { "spec", "spec" }, { "rb|rbw", "ruby" },
			{ "sl", "slang" }, { "scala", "scala" }, { "sh", "sh" },
			{ "sml", "sml" }, { "tcl", "tcl" },
			{ "xml|xsml|xsl|xsd|kml|wsdl", "xml" }, { "xorg", "xorg" },
			{ "hx", "haxe" } };

	public DocumentHandler() {
	}

	public DocumentHandler(String extension) {
		// mimeType=extension;
		for (int i = 0; i < ExtentsionMap.length; i++) {
			if (extension.matches(ExtentsionMap[i][0])) {
				mimeType = ExtentsionMap[i][1];
				break;
			}
		}
		if (mimeType == null && mimeType.isEmpty()) {
			mimeType = "c";
		}

	}

	public String getFileExtension() {
		return mimeType;
	}

	public String getFileMimeType() {
		return "text/html";
	}

	public String getFilePrettifyClass() {
		return "sh_" + mimeType;
	}

	public String getFileFormattedString(String fileString) {
		return TextUtils.htmlEncode(fileString);//.replace("\n", "<br>");
	}

	public String getFileScriptFiles() {
		return "<script src='file:///android_asset/lang/" + "sh_" + mimeType
				+ ".min.js" + "' type='text/javascript'></script> ";
	}
}
