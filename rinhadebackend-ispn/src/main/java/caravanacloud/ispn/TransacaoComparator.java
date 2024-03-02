package caravanacloud.ispn;

import java.io.Serializable;
import java.util.Comparator;

public class TransacaoComparator implements Serializable, Comparator<Transacao> {
    @Override
      public int compare(Transacao o1, Transacao o2) {
          return o2.realizadaEm.compareTo(o1.realizadaEm);
      }
  }