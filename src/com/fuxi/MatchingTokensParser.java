package com.fuxi;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.search.ValueSourceParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchingTokensParser extends ValueSourceParser {
	private static Logger LOGGER = LoggerFactory.getLogger(MatchingTokensParser.class);
	
	@Override
	public void init(NamedList namedList) {
	}

	private <T extends Enum<T>> T castToEnum(String value, Class<T> enumType, T default_value) throws SyntaxError {
		T enum_obj = default_value;
		
		try {
			enum_obj = T.valueOf(enumType, value);
		} catch (IllegalArgumentException e) {
			String possibleValues = StringUtils.join(enum_obj.getDeclaringClass().getEnumConstants(), ", ");
			String e_msg = enumType.getSimpleName() + " argument value " + value + " is not valid. Valid " + enumType.getSimpleName() + ": " + possibleValues;

			LOGGER.error(e_msg);
			throw new SyntaxError(e_msg);
		} catch (NullPointerException e) {
			LOGGER.info(enumType.getSimpleName() + " argument not available default will be used: " + enum_obj.toString());
		}
		
		return enum_obj;
	}
	
	@Override
	public ValueSource parse(FunctionQParser fqp) throws SyntaxError {
		String field = fqp.parseId();
		ValueSource qterms = fqp.parseValueSource();
		String result_type_str = fqp.parseArg();
		String matching_type_str = fqp.parseArg();
		int filters_no = fqp.getParams().getFieldInt(field, "filters", 0);

		MatchingTokensFunction.ResultType result_type = castToEnum(result_type_str,
																   MatchingTokensFunction.ResultType.class,
																   MatchingTokensFunction.ResultType.term);
		
		MatchingTokensFunction.MatchingType matching_type = castToEnum(matching_type_str,
																	   MatchingTokensFunction.MatchingType.class,
																	   MatchingTokensFunction.MatchingType.simple);
		
		return new MatchingTokensFunction(field, qterms, result_type, matching_type, fqp.getReq().getSearcher(), filters_no);
	}

}
