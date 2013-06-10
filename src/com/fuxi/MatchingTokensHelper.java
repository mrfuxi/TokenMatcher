package com.fuxi;

import java.io.IOException;
import java.util.List;

import org.apache.solr.analysis.TokenizerChain;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.schema.FieldType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchingTokensHelper extends SearchComponent {
	private static Logger LOGGER = LoggerFactory.getLogger(MatchingTokensHelper.class);
	
	@Override
	public void process(ResponseBuilder rb) throws IOException {
		String fields[] = rb.req.getParams().get("df", "").split(",");
		String terms = rb.req.getParams().get("terms");
		
		if (terms == null || fields.length == 0) {
			// not enough info to run component
			return;
		}
		
		NamedList<NamedList<String>> response = new SimpleOrderedMap<NamedList<String>>();
		
		for (String field: fields) {
			FieldType field_type = rb.req.getSchema().getField(field).getType();
			TokenizerChain qanalyzer = (TokenizerChain)field_type.getQueryAnalyzer();
			List<TokenInfo> tokenized_terms = MatchingTokensFunction.getTokenizedTerms(qanalyzer, field, terms);
			
			NamedList<String> tokensList = new NamedList<String>();
			
			for (TokenInfo token: tokenized_terms) {
				tokensList.add(token.getTermId().toString(), token.getTerm());
			}
			
			response.add(field, tokensList);
		}
		
		rb.rsp.add(getName(), response);
	}

	@Override
	public void prepare(ResponseBuilder rb) throws IOException {
		//Nothing here
	}

	@Override
	public String getDescription() {
		return "Helper component for MatchingTokens funtion";
	}
    
	@Override
    public String getVersion() {
        return "1.0";
    }
    
	@Override
	public String getSource() {
		return "https://github.com/mrfuxi/TokenMatcher";
	}
}
