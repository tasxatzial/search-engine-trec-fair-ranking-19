package gr.csd.uoc.hy463.themis.queryExpansion;

import gr.csd.uoc.hy463.themis.queryExpansion.Exceptions.ExpansionDictionaryInitException;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import net.sf.extjwnl.JWNLException;

import java.io.IOException;
import java.util.List;

/**
 * Abstract class for all query expansion models
 */
public abstract class QueryExpansion {
    public enum DICTIONARY {
        NONE, GLOVE, EXTJWNL
    }

    /**
     * Expands a given query using an expansion dictionary. The first term in each List<QueryTerm>
     * should be the original term.
     *
     * @param query
     * @return
     * @throws ExpansionDictionaryInitException
     * @throws JWNLException
     */
    public abstract List<List<QueryTerm>> expandQuery(List<String> query)
            throws ExpansionDictionaryInitException, JWNLException, IOException;
}
