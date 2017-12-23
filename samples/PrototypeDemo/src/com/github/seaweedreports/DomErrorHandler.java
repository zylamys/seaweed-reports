/**
 * 
 */
package com.github.seaweedreports;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.github.seaweedreports.LogFactory;

public class DomErrorHandler implements ErrorHandler {
	@Override
	public void error(SAXParseException arg0) throws SAXException {
		LogFactory.getLog().error(arg0.getMessage(), arg0);

	}

	@Override
	public void fatalError(SAXParseException arg0) throws SAXException {
		LogFactory.getLog().error(arg0.getMessage(), arg0);

	}

	@Override
	public void warning(SAXParseException arg0) throws SAXException {
		LogFactory.getLog().warn(arg0.getMessage(), arg0);
	}

}
