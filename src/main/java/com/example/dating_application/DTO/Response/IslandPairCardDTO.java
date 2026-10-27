package com.example.dating_application.DTO.Response;

public class IslandPairCardDTO {
    private String styleCode;
    private String styleTitle;
    private String styleText;
    private boolean rare;
    private String signatureCode;
    private String signatureTitle;
    private IslandRoleDTO myRole;
    private IslandRoleDTO partnerRole;

    public IslandPairCardDTO() {
    }

    public String getStyleCode() {
        return styleCode;
    }

    public void setStyleCode(String styleCode) {
        this.styleCode = styleCode;
    }

    public String getStyleTitle() {
        return styleTitle;
    }

    public void setStyleTitle(String styleTitle) {
        this.styleTitle = styleTitle;
    }

    public String getStyleText() {
        return styleText;
    }

    public void setStyleText(String styleText) {
        this.styleText = styleText;
    }

    public boolean isRare() {
        return rare;
    }

    public void setRare(boolean rare) {
        this.rare = rare;
    }

    public String getSignatureCode() {
        return signatureCode;
    }

    public void setSignatureCode(String signatureCode) {
        this.signatureCode = signatureCode;
    }

    public String getSignatureTitle() {
        return signatureTitle;
    }

    public void setSignatureTitle(String signatureTitle) {
        this.signatureTitle = signatureTitle;
    }

    public IslandRoleDTO getMyRole() {
        return myRole;
    }

    public void setMyRole(IslandRoleDTO myRole) {
        this.myRole = myRole;
    }

    public IslandRoleDTO getPartnerRole() {
        return partnerRole;
    }

    public void setPartnerRole(IslandRoleDTO partnerRole) {
        this.partnerRole = partnerRole;
    }
}
