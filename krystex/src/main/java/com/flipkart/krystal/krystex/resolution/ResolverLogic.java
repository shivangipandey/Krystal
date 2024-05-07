package com.flipkart.krystal.krystex.resolution;

import com.flipkart.krystal.data.Facets;
import com.flipkart.krystal.data.RequestBuilder;
import com.flipkart.krystal.krystex.Logic;
import com.flipkart.krystal.resolution.ResolverCommand;
import com.google.common.collect.ImmutableList;

public non-sealed interface ResolverLogic extends Logic {
  ResolverCommand resolve(ImmutableList<RequestBuilder<Object>> depRequests, Facets facets);
}
