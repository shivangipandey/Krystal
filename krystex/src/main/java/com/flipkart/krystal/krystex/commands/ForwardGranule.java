package com.flipkart.krystal.krystex.commands;

import com.flipkart.krystal.data.Facets;
import com.flipkart.krystal.krystex.kryon.DependantChain;
import com.flipkart.krystal.krystex.kryon.KryonId;
import com.flipkart.krystal.krystex.request.RequestId;
import com.google.common.collect.ImmutableSet;

public record ForwardGranule(
    KryonId kryonId,
    ImmutableSet<String> inputNames,
    Facets values,
    DependantChain dependantChain,
    RequestId requestId)
    implements GranularCommand {}
