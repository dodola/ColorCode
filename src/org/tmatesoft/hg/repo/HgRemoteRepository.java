/*
 * Copyright (c) 2011 TMate Software Ltd
 *  
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * For information on how to redistribute this software under
 * the terms of a license other than GNU General Public License
 * contact TMate Software at support@hg4j.com
 */
package org.tmatesoft.hg.repo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StreamTokenizer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.tmatesoft.hg.core.HgBadArgumentException;
import org.tmatesoft.hg.core.HgBadStateException;
import org.tmatesoft.hg.core.HgInvalidFileException;
import org.tmatesoft.hg.core.HgRemoteConnectionException;
import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.core.SessionContext;

/**
 * WORK IN PROGRESS, DO NOT USE
 * 
 * @see http://mercurial.selenic.com/wiki/WireProtocol
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgRemoteRepository {
	
	private final URL url;
	private final SSLContext sslContext;
	private final String authInfo;
	private final boolean debug = Boolean.parseBoolean(System.getProperty("hg4j.remote.debug"));
	private HgLookup lookupHelper;
	private final SessionContext sessionContext;

	HgRemoteRepository(SessionContext ctx, URL url) throws HgBadArgumentException {
		if (url == null || ctx == null) {
			throw new IllegalArgumentException();
		}
		this.url = url;
		sessionContext = ctx;
		if ("https".equals(url.getProtocol())) {
			try {
				sslContext = SSLContext.getInstance("SSL");
				class TrustEveryone implements X509TrustManager {
					public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
						if (debug) {
							System.out.println("checkClientTrusted:" + authType);
						}
					}
					public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
						if (debug) {
							System.out.println("checkServerTrusted:" + authType);
						}
					}
					public X509Certificate[] getAcceptedIssuers() {
						return new X509Certificate[0];
					}
				};
				sslContext.init(null, new TrustManager[] { new TrustEveryone() }, null);
			} catch (Exception ex) {
				throw new HgBadArgumentException("Can't initialize secure connection", ex);
			}
		} else {
			sslContext = null;
		}
		if (url.getUserInfo() != null) {
			String ai = null;
			try {
				// Hack to get Base64-encoded credentials
				Preferences tempNode = Preferences.userRoot().node("xxx");
				tempNode.putByteArray("xxx", url.getUserInfo().getBytes());
				ai = tempNode.get("xxx", null);
				tempNode.removeNode();
			} catch (BackingStoreException ex) {
				sessionContext.getLog().info(getClass(), ex, null);
				// IGNORE
			}
			authInfo = ai;
		} else {
			authInfo = null;
		}
	}
	
	public boolean isInvalid() throws HgRemoteConnectionException {
		// say hello to server, check response
		if (Boolean.FALSE.booleanValue()) {
			throw HgRepository.notImplemented();
		}
		return false; // FIXME implement remote repository hello/check
	}

	/**
	 * @return human-readable address of the server, without user credentials or any other security information
	 */
	public String getLocation() {
		if (url.getUserInfo() == null) {
			return url.toExternalForm();
		}
		if (url.getPort() != -1) {
			return String.format("%s://%s:%d%s", url.getProtocol(), url.getHost(), url.getPort(), url.getPath());
		} else {
			return String.format("%s://%s%s", url.getProtocol(), url.getHost(), url.getPath());
		}
	}

	public List<Nodeid> heads() throws HgRemoteConnectionException {
		try {
			URL u = new URL(url, url.getPath() + "?cmd=heads");
			HttpURLConnection c = setupConnection(u.openConnection());
			c.connect();
			if (debug) {
				dumpResponseHeader(u, c);
			}
			InputStreamReader is = new InputStreamReader(c.getInputStream(), "US-ASCII");
			StreamTokenizer st = new StreamTokenizer(is);
			st.ordinaryChars('0', '9');
			st.wordChars('0', '9');
			st.eolIsSignificant(false);
			LinkedList<Nodeid> parseResult = new LinkedList<Nodeid>();
			while (st.nextToken() != StreamTokenizer.TT_EOF) {
				parseResult.add(Nodeid.fromAscii(st.sval));
			}
			return parseResult;
		} catch (MalformedURLException ex) {
			throw new HgRemoteConnectionException("Bad URL", ex).setRemoteCommand("heads").setServerInfo(getLocation());
		} catch (IOException ex) {
			throw new HgRemoteConnectionException("Communication failure", ex).setRemoteCommand("heads").setServerInfo(getLocation());
		}
	}
	
	public List<Nodeid> between(Nodeid tip, Nodeid base) throws HgRemoteConnectionException {
		Range r = new Range(base, tip);
		// XXX shall handle errors like no range key in the returned map, not sure how.
		return between(Collections.singletonList(r)).get(r);
	}

	/**
	 * @param ranges
	 * @return map, where keys are input instances, values are corresponding server reply
	 * @throws HgRemoteConnectionException 
	 */
	public Map<Range, List<Nodeid>> between(Collection<Range> ranges) throws HgRemoteConnectionException {
		if (ranges.isEmpty()) {
			return Collections.emptyMap();
		}
		// if fact, shall do other way round, this method shall send 
		LinkedHashMap<Range, List<Nodeid>> rv = new LinkedHashMap<HgRemoteRepository.Range, List<Nodeid>>(ranges.size() * 4 / 3);
		StringBuilder sb = new StringBuilder(20 + ranges.size() * 82);
		sb.append("pairs=");
		for (Range r : ranges) {
			sb.append(r.end.toString());
			sb.append('-');
			sb.append(r.start.toString());
			sb.append('+');
		}
		if (sb.charAt(sb.length() - 1) == '+') {
			// strip last space 
			sb.setLength(sb.length() - 1);
		}
		try {
			boolean usePOST = ranges.size() > 3;
			URL u = new URL(url, url.getPath() + "?cmd=between" + (usePOST ? "" : '&' + sb.toString()));
			HttpURLConnection c = setupConnection(u.openConnection());
			if (usePOST) {
				c.setRequestMethod("POST");
				c.setRequestProperty("Content-Length", String.valueOf(sb.length()/*nodeids are ASCII, bytes == characters */));
				c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				c.setDoOutput(true);
				c.connect();
				OutputStream os = c.getOutputStream();
				os.write(sb.toString().getBytes());
				os.flush();
				os.close();
			} else {
				c.connect();
			}
			if (debug) {
				System.out.printf("%d ranges, method:%s \n", ranges.size(), c.getRequestMethod());
				dumpResponseHeader(u, c);
			}
			InputStreamReader is = new InputStreamReader(c.getInputStream(), "US-ASCII");
			StreamTokenizer st = new StreamTokenizer(is);
			st.ordinaryChars('0', '9');
			st.wordChars('0', '9');
			st.eolIsSignificant(true);
			Iterator<Range> rangeItr = ranges.iterator();
			LinkedList<Nodeid> currRangeList = null;
			Range currRange = null;
			boolean possiblyEmptyNextLine = true;
			while (st.nextToken() != StreamTokenizer.TT_EOF) {
				if (st.ttype == StreamTokenizer.TT_EOL) {
					if (possiblyEmptyNextLine) {
						// newline follows newline;
						assert currRange == null;
						assert currRangeList == null;
						if (!rangeItr.hasNext()) {
							throw new HgBadStateException();
						}
						rv.put(rangeItr.next(), Collections.<Nodeid>emptyList());
					} else {
						if (currRange == null || currRangeList == null) {
							throw new HgBadStateException();
						}
						// indicate next range value is needed
						currRange = null;
						currRangeList = null;
						possiblyEmptyNextLine = true;
					}
				} else {
					possiblyEmptyNextLine = false;
					if (currRange == null) {
						if (!rangeItr.hasNext()) {
							throw new HgBadStateException();
						}
						currRange = rangeItr.next();
						currRangeList = new LinkedList<Nodeid>();
						rv.put(currRange, currRangeList);
					}
					Nodeid nid = Nodeid.fromAscii(st.sval);
					currRangeList.addLast(nid);
				}
			}
			is.close();
			return rv;
		} catch (MalformedURLException ex) {
			throw new HgRemoteConnectionException("Bad URL", ex).setRemoteCommand("between").setServerInfo(getLocation());
		} catch (IOException ex) {
			throw new HgRemoteConnectionException("Communication failure", ex).setRemoteCommand("between").setServerInfo(getLocation());
		}
	}

	public List<RemoteBranch> branches(List<Nodeid> nodes) throws HgRemoteConnectionException {
		StringBuilder sb = new StringBuilder(20 + nodes.size() * 41);
		sb.append("nodes=");
		for (Nodeid n : nodes) {
			sb.append(n.toString());
			sb.append('+');
		}
		if (sb.charAt(sb.length() - 1) == '+') {
			// strip last space 
			sb.setLength(sb.length() - 1);
		}
		try {
			URL u = new URL(url, url.getPath() + "?cmd=branches&" + sb.toString());
			HttpURLConnection c = setupConnection(u.openConnection());
			c.connect();
			if (debug) {
				dumpResponseHeader(u, c);
			}
			InputStreamReader is = new InputStreamReader(c.getInputStream(), "US-ASCII");
			StreamTokenizer st = new StreamTokenizer(is);
			st.ordinaryChars('0', '9');
			st.wordChars('0', '9');
			st.eolIsSignificant(false);
			ArrayList<Nodeid> parseResult = new ArrayList<Nodeid>(nodes.size() * 4);
			while (st.nextToken() != StreamTokenizer.TT_EOF) {
				parseResult.add(Nodeid.fromAscii(st.sval));
			}
			if (parseResult.size() != nodes.size() * 4) {
				throw new HgRemoteConnectionException(String.format("Bad number of nodeids in result (shall be factor 4), expected %d, got %d", nodes.size()*4, parseResult.size()));
			}
			ArrayList<RemoteBranch> rv = new ArrayList<RemoteBranch>(nodes.size());
			for (int i = 0; i < nodes.size(); i++) {
				RemoteBranch rb = new RemoteBranch(parseResult.get(i*4), parseResult.get(i*4 + 1), parseResult.get(i*4 + 2), parseResult.get(i*4 + 3));
				rv.add(rb);
			}
			return rv;
		} catch (MalformedURLException ex) {
			throw new HgRemoteConnectionException("Bad URL", ex).setRemoteCommand("branches").setServerInfo(getLocation());
		} catch (IOException ex) {
			throw new HgRemoteConnectionException("Communication failure", ex).setRemoteCommand("branches").setServerInfo(getLocation());
		}
	}

	/*
	 * XXX need to describe behavior when roots arg is empty; our RepositoryComparator code currently returns empty lists when
	 * no common elements found, which in turn means we need to query changes starting with NULL nodeid.
	 * 
	 * WireProtocol wiki: roots = a list of the latest nodes on every service side changeset branch that both the client and server know about.
	 * 
	 * Perhaps, shall be named 'changegroup'

	 * Changegroup: 
	 * http://mercurial.selenic.com/wiki/Merge 
	 * http://mercurial.selenic.com/wiki/WireProtocol 
	 * 
	 * according to latter, bundleformat data is sent through zlib
	 * (there's no header like HG10?? with the server output, though, 
	 * as one may expect according to http://mercurial.selenic.com/wiki/BundleFormat)
	 */
	public HgBundle getChanges(List<Nodeid> roots) throws HgRemoteConnectionException, HgInvalidFileException {
		List<Nodeid> _roots = roots.isEmpty() ? Collections.singletonList(Nodeid.NULL) : roots;
		StringBuilder sb = new StringBuilder(20 + _roots.size() * 41);
		sb.append("roots=");
		for (Nodeid n : _roots) {
			sb.append(n.toString());
			sb.append('+');
		}
		if (sb.charAt(sb.length() - 1) == '+') {
			// strip last space 
			sb.setLength(sb.length() - 1);
		}
		try {
			URL u = new URL(url, url.getPath() + "?cmd=changegroup&" + sb.toString());
			HttpURLConnection c = setupConnection(u.openConnection());
			c.connect();
			if (debug) {
				dumpResponseHeader(u, c);
			}
			File tf = writeBundle(c.getInputStream(), false, "HG10GZ" /*didn't see any other that zip*/);
			if (debug) {
				System.out.printf("Wrote bundle %s for roots %s\n", tf, sb);
			}
			return getLookupHelper().loadBundle(tf);
		} catch (MalformedURLException ex) { // XXX in fact, this exception might be better to be re-thrown as RuntimeEx,
			// as there's little user can do about this issue (URLs are constructed by our code)
			throw new HgRemoteConnectionException("Bad URL", ex).setRemoteCommand("changegroup").setServerInfo(getLocation());
		} catch (IOException ex) {
			throw new HgRemoteConnectionException("Communication failure", ex).setRemoteCommand("changegroup").setServerInfo(getLocation());
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + '[' + getLocation() + ']';
	}

	private HgLookup getLookupHelper() {
		if (lookupHelper == null) {
			lookupHelper = new HgLookup(sessionContext);
		}
		return lookupHelper;
	}
	
	private HttpURLConnection setupConnection(URLConnection urlConnection) {
		urlConnection.setRequestProperty("User-Agent", "hg4j/0.5.0");
		urlConnection.addRequestProperty("Accept", "application/mercurial-0.1");
		if (authInfo != null) {
			urlConnection.addRequestProperty("Authorization", "Basic " + authInfo);
		}
		if (sslContext != null) {
			((HttpsURLConnection) urlConnection).setSSLSocketFactory(sslContext.getSocketFactory());
		}
		return (HttpURLConnection) urlConnection;
	}

	private void dumpResponseHeader(URL u, HttpURLConnection c) {
		System.out.printf("Query (%d bytes):%s\n", u.getQuery().length(), u.getQuery());
		System.out.println("Response headers:");
		final Map<String, List<String>> headerFields = c.getHeaderFields();
		for (String s : headerFields.keySet()) {
			System.out.printf("%s: %s\n", s, c.getHeaderField(s));
		}
	}
	
	private static File writeBundle(InputStream is, boolean decompress, String header) throws IOException {
		InputStream zipStream = decompress ? new InflaterInputStream(is) : is;
		File tf = File.createTempFile("hg-bundle-", null);
		FileOutputStream fos = new FileOutputStream(tf);
		fos.write(header.getBytes());
		int r;
		byte[] buf = new byte[8*1024];
		while ((r = zipStream.read(buf)) != -1) {
			fos.write(buf, 0, r);
		}
		fos.close();
		zipStream.close();
		return tf;
	}


	public static final class Range {
		/**
		 * Root of the range, earlier revision
		 */
		public final Nodeid start;
		/**
		 * Head of the range, later revision.
		 */
		public final Nodeid end;
		
		/**
		 * @param from - root/base revision
		 * @param to - head/tip revision
		 */
		public Range(Nodeid from, Nodeid to) {
			start = from;
			end = to;
		}
	}

	public static final class RemoteBranch {
		public final Nodeid head, root, p1, p2;
		
		public RemoteBranch(Nodeid h, Nodeid r, Nodeid parent1, Nodeid parent2) {
			head = h;
			root = r;
			p1 = parent1;
			p2 = parent2;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (false == obj instanceof RemoteBranch) {
				return false;
			}
			RemoteBranch o = (RemoteBranch) obj;
			// in fact, p1 and p2 are not supposed to be null, ever (at least for RemoteBranch created from server output)
			return head.equals(o.head) && root.equals(o.root) && (p1 == null && o.p1 == null || p1.equals(o.p1)) && (p2 == null && o.p2 == null || p2.equals(o.p2));
		}
	}
}
