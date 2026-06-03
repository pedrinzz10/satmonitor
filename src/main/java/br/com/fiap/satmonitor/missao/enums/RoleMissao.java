package br.com.fiap.satmonitor.missao.enums;

public enum RoleMissao {
    DONO,
    SUPERVISOR,
    MEMBRO;

    public boolean temPermissao(RoleMissao minimo) {
        return this.ordinal() <= minimo.ordinal();
    }
}
