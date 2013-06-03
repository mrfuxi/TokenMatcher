package com.fuxi;

import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.search.Query;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.search.ValueSourceParser;

public class MatchingTokensParser extends ValueSourceParser {
	
	@Override
	public void init(NamedList namedList) {
	}

	@Override
	public ValueSource parse(FunctionQParser fqp) throws SyntaxError {
		String field = fqp.parseId();
		String qterms = fqp.parseArg();
		
		//ValueSource source = fqp.parseValueSource();
		//return new MatchingTokensFunction(source);
		return new MatchingTokensFunction(field, qterms);
	}

}
