const SITE_SOCIAL_LINK_KEYS = ["vk", "max", "youtube", "instagram", "telegram", "facebook", "tiktok"];
const PRESENCE_HEARTBEAT_INTERVAL_MS = 45_000;

const state = {
  userId: 0,
  isAuthenticated: false,
  csrfToken: "",
  username: "",
  userEmail: "",
  userEmailVerified: false,
  userFirstName: "",
  userLastName: "",
  userPhone: "",
  userOauthProvider: "",
  referralProgram: {},
  isAdmin: false,
  isAdminAccount: false,
  access: null,
  payments: null,
  siteSocialLinks: {},
  billing: null,
  maintenance: null,
  maintenanceTimer: 0,
  presenceTimer: 0,
  presenceInFlight: false,
  accountOauth: {
    connections: [],
    loaded: false,
    vkInitialized: false,
    vkInitPromise: null,
    vkProviderCodes: "",
    vkCodeVerifier: "",
    vkState: "",
    vkWidgets: [],
  },
  accountEmail: {
    pendingEmail: "",
    verificationStarted: false,
  },
  currentTab: "home",
  categories: [],
  sausagesByCategory: new Map(),
  selectedCategory: "",
  selectedSausage: "",
  recipeSearchQuery: "",
  recipeLoadRequestId: 0,
  currentRecipe: null,
  handbookSections: [],
  articles: [],
  selectedArticleId: 0,
  articleDetail: null,
  articleReply: { parentId: 0, nickname: "", form: null },
  articleCommentEditor: { commentId: 0, form: null },
  equipment: [],
  faq: [],
  myTickets: [],
  lastCustomLines: "",
  lastRecipeCalc: null,
  lastCustomCalc: null,
  lastCustomCalcWeight: 1,
  notifications: {
    items: [],
    unreadCount: 0,
    readCount: 0,
    openedId: 0,
    loaded: false,
    highestId: 0,
    audioContext: null,
    serviceWorkerRegistration: null,
    serviceWorkerPromise: null,
    pushSubscriptionActive: false,
    pushSyncAttempted: false,
    refreshTimer: null,
    eventSource: null,
  },
  pendingRequests: 0,
  toastTimer: null,
  adminDbAutoRefreshTimer: null,
  /** После «Применить» промокода: перерасчёт только для совпадающего tier (скидка на 1-й период). */
  promoPreview: null,
  admin: {
    loaded: false,
    dashboard: {
      data: null,
      loadedAt: 0,
    },
    categories: [],
    handbookSections: [],
    articles: [],
    subscriptionPlans: [],
    promocodes: [],
    faq: [],
    equipment: [],
    tickets: [],
    users: [],
    ordersStatus: "current",
    orderClients: [],
    carts: [],
    cartsSummary: {},
    digitalSalesStatus: "delivered",
    digitalSales: [],
    digitalSalesSummary: [],
    digitalSalesTotal: 0,
    digitalBook: {},
    digitalBookUploading: false,
    notifications: [],
    siteSocialLinks: {},
    maintenance: {},
    registrationBonusSettings: {},
    audit: {
      limit: 100,
      offset: 0,
      total: 0,
      items: [],
    },
    dbLog: {
      items: [],
      total: 0,
      limit: 100,
      offset: 0,
      lastFilters: {},
    },
    recipeOriginalIngredientNames: new Set(),
    pendingRecipeSlug: "",
    articleSelectionRange: null,
  },
};

const el = {
  globalLoading: document.getElementById("globalLoading"),
  appToast: document.getElementById("appToast"),
  cookieNoticeBar: document.getElementById("cookieNoticeBar"),
  cookieNoticeAccept: document.getElementById("cookieNoticeAccept"),
  pdConsentGate: document.getElementById("pdConsentGate"),
  pdConsentCheckbox: document.getElementById("pdConsentCheckbox"),
  pdConsentSubmit: document.getElementById("pdConsentSubmit"),

  tabs: Array.from(document.querySelectorAll(".tab, .mobile-nav__item[data-tab], .mobile-nav-sheet__item[data-tab]")),
  panels: Array.from(document.querySelectorAll(".panel")),
  mobileBottomNav: document.getElementById("mobileBottomNav"),
  mobileNavMoreBtn: document.getElementById("mobileNavMoreBtn"),
  mobileNavSheet: document.getElementById("mobileNavSheet"),

  welcomeText: document.getElementById("welcomeText"),
  heroImage: document.getElementById("heroImage"),
  heroSocialLinks: document.getElementById("heroSocialLinks"),
  homeStats: document.getElementById("homeStats"),
  homeAccessInfo: document.getElementById("homeAccessInfo"),
  storePromoBanner: document.getElementById("storePromoBanner"),
  storePromoPrev: document.getElementById("storePromoPrev"),
  storePromoNext: document.getElementById("storePromoNext"),
  guestIntroCta: document.getElementById("guestIntroCta"),
  guestLoginBtn: document.getElementById("guestLoginBtn"),
  guestRegisterBtn: document.getElementById("guestRegisterBtn"),

  userIdBadge: document.getElementById("userIdBadge"),
  userRoleBadge: document.getElementById("userRoleBadge"),
  userTierBadge: document.getElementById("userTierBadge"),
  authBtn: document.getElementById("authBtn"),
  logoutBtn: document.getElementById("logoutBtn"),

  accessAlert: document.getElementById("accessAlert"),

  paywallTabBtn: document.getElementById("paywallTabBtn"),
  adminTabBtn: document.getElementById("adminTabBtn"),
  accountTabBtn: document.getElementById("accountTabBtn"),
  notificationsBtn: document.getElementById("notificationsBtn"),
  notificationsBadge: document.getElementById("notificationsBadge"),
  notificationsPopover: document.getElementById("notificationsPopover"),
  notificationsList: document.getElementById("notificationsList"),
  notificationsReadAllBtn: document.getElementById("notificationsReadAllBtn"),
  notificationsClearBtn: document.getElementById("notificationsClearBtn"),
  notificationPermissionPrompt: document.getElementById("notificationPermissionPrompt"),
  notificationPermissionEnableBtn: document.getElementById("notificationPermissionEnableBtn"),
  notificationPermissionDismissBtn: document.getElementById("notificationPermissionDismissBtn"),
  accountGreetingName: document.getElementById("accountGreetingName"),
  accountInfo: document.getElementById("accountInfo"),
  accountProfileState: document.getElementById("accountProfileState"),
  accountSubscriptionCard: document.getElementById("accountSubscriptionCard"),
  accountUpgradeCard: document.getElementById("accountUpgradeCard"),
  accountEmailLinkForm: document.getElementById("accountEmailLinkForm"),
  accountEmailRequestFields: document.getElementById("accountEmailRequestFields"),
  accountEmailInput: document.getElementById("accountEmailInput"),
  accountEmailSendBtn: document.getElementById("accountEmailSendBtn"),
  accountEmailVerifyFields: document.getElementById("accountEmailVerifyFields"),
  accountEmailPendingAddress: document.getElementById("accountEmailPendingAddress"),
  accountEmailCodeInput: document.getElementById("accountEmailCodeInput"),
  accountEmailConfirmBtn: document.getElementById("accountEmailConfirmBtn"),
  accountEmailResendBtn: document.getElementById("accountEmailResendBtn"),
  accountEmailChangeBtn: document.getElementById("accountEmailChangeBtn"),
  accountEmailResult: document.getElementById("accountEmailResult"),
  accountOauthCard: document.getElementById("accountOauthCard"),
  accountOauthSummary: document.getElementById("accountOauthSummary"),
  accountOauthConnections: document.getElementById("accountOauthConnections"),
  accountOauthConsent: document.getElementById("accountOauthConsent"),
  accountOauthRefresh: document.getElementById("accountOauthRefresh"),
  accountOauthResult: document.getElementById("accountOauthResult"),
  accountLinkGoogleBtn: document.getElementById("accountLinkGoogleBtn"),
  accountVkProviderVk: document.getElementById("accountVkProviderVk"),
  accountVkProviderMail: document.getElementById("accountVkProviderMail"),
  accountVkProviderOk: document.getElementById("accountVkProviderOk"),
  billingInfo: document.getElementById("billingInfo"),
  billingResult: document.getElementById("billingResult"),
  disableAutopayBtn: document.getElementById("disableAutopayBtn"),
  cardBindBtn: document.getElementById("cardBindBtn"),
  sbpBindBtn: document.getElementById("sbpBindBtn"),
  sbpQrWrap: document.getElementById("sbpQrWrap"),
  accountPlanNotice: document.getElementById("accountPlanNotice"),
  accountPlans: document.getElementById("accountPlans"),
  paywallMessage: document.getElementById("paywallMessage"),
  premiumLink: document.getElementById("premiumLink"),
  groupLink: document.getElementById("groupLink"),
  premiumPlanTitle: document.getElementById("premiumPlanTitle"),
  premiumPlanDescription: document.getElementById("premiumPlanDescription"),
  premiumPlanFeatures: document.getElementById("premiumPlanFeatures"),
  paymentPromoCode: document.getElementById("paymentPromoCode"),
  applyPaymentPromoCodeBtn: document.getElementById("applyPaymentPromoCodeBtn"),

  categorySelect: document.getElementById("categorySelect"),
  recipeSearchInput: document.getElementById("recipeSearchInput"),
  sausageSelect: document.getElementById("sausageSelect"),
  recipeTitle: document.getElementById("recipeTitle"),
  recipeImage: document.getElementById("recipeImage"),
  recipeText: document.getElementById("recipeText"),
  recipeIngredients: document.getElementById("recipeIngredients"),
  recipeCalcForm: document.getElementById("recipeCalcForm"),
  recipeCalcResult: document.getElementById("recipeCalcResult"),
  recipeCartHint: document.getElementById("recipeCartHint"),
  recipePrintPdfBtn: document.getElementById("recipePrintPdfBtn"),
  recipeAddToCartBtn: document.getElementById("recipeAddToCartBtn"),

  customCalcForm: document.getElementById("customCalcForm"),
  customCalcResult: document.getElementById("customCalcResult"),
  customPrintPdfBtn: document.getElementById("customPrintPdfBtn"),
  customIngredientsRows: document.getElementById("customIngredientsRows"),
  customAddIngredientRow: document.getElementById("customAddIngredientRow"),

  handbookSectionSelect: document.getElementById("handbookSectionSelect"),
  handbookArticleSelect: document.getElementById("handbookArticleSelect"),
  handbookArticleTitle: document.getElementById("handbookArticleTitle"),
  handbookArticleText: document.getElementById("handbookArticleText"),
  handbookArticleLink: document.getElementById("handbookArticleLink"),

  articlesList: document.getElementById("articlesList"),
  articlesShell: document.querySelector(".articles-shell"),
  articleDetail: document.getElementById("articleDetail"),
  articleMobileBackBtn: document.getElementById("articleMobileBackBtn"),
  articleEmpty: document.getElementById("articleEmpty"),
  articleView: document.getElementById("articleView"),
  articleCover: document.getElementById("articleCover"),
  articlePublishedAt: document.getElementById("articlePublishedAt"),
  articleAuthor: document.getElementById("articleAuthor"),
  articleShareBtn: document.getElementById("articleShareBtn"),
  articleTitle: document.getElementById("articleTitle"),
  articleContent: document.getElementById("articleContent"),
  articleLikeBtn: document.getElementById("articleLikeBtn"),
  articleDislikeBtn: document.getElementById("articleDislikeBtn"),
  articleCommentsCount: document.getElementById("articleCommentsCount"),
  articleCommentForm: document.getElementById("articleCommentForm"),
  articleCommentText: document.getElementById("articleCommentText"),
  articleCommentLoginHint: document.getElementById("articleCommentLoginHint"),
  articleCommentsList: document.getElementById("articleCommentsList"),

  equipmentGrid: document.getElementById("equipmentGrid"),
  equipmentDialog: document.getElementById("equipmentDialog"),
  equipmentDialogTitle: document.getElementById("equipmentDialogTitle"),
  equipmentDialogText: document.getElementById("equipmentDialogText"),
  equipmentDialogImage: document.getElementById("equipmentDialogImage"),
  equipmentDialogLink: document.getElementById("equipmentDialogLink"),
  equipmentDialogShare: document.getElementById("equipmentDialogShare"),
  equipmentDialogReviews: document.getElementById("equipmentDialogReviews"),

  faqList: document.getElementById("faqList"),
  supportForm: document.getElementById("supportForm"),
  supportResult: document.getElementById("supportResult"),
  myTickets: document.getElementById("myTickets"),
  reloadMyTickets: document.getElementById("reloadMyTickets"),

  adminSection: document.getElementById("tab-admin"),
  adminTabsWrap: document.getElementById("adminTabs"),
  adminPanels: Array.from(document.querySelectorAll(".admin-panel")),
  adminDashboardAdminName: document.getElementById("adminDashboardAdminName"),
  adminDashboardUpdatedAt: document.getElementById("adminDashboardUpdatedAt"),
  adminDashboardRefreshBtn: document.getElementById("adminDashboardRefreshBtn"),
  adminDashboardRevenue: document.getElementById("adminDashboardRevenue"),
  adminDashboardRevenueTrend: document.getElementById("adminDashboardRevenueTrend"),
  adminDashboardOrders: document.getElementById("adminDashboardOrders"),
  adminDashboardOrdersNote: document.getElementById("adminDashboardOrdersNote"),
  adminDashboardSubscriptions: document.getElementById("adminDashboardSubscriptions"),
  adminDashboardOnline: document.getElementById("adminDashboardOnline"),
  adminDashboardOnlineNote: document.getElementById("adminDashboardOnlineNote"),
  adminDashboardUsers: document.getElementById("adminDashboardUsers"),
  adminDashboardUsersNote: document.getElementById("adminDashboardUsersNote"),
  adminDashboardTickets: document.getElementById("adminDashboardTickets"),
  adminDashboardTicketsNote: document.getElementById("adminDashboardTicketsNote"),
  adminDashboardError: document.getElementById("adminDashboardError"),
  adminDashboardChart: document.getElementById("adminDashboardChart"),
  adminDashboardChartTotal: document.getElementById("adminDashboardChartTotal"),
  adminDashboardStoreRevenue: document.getElementById("adminDashboardStoreRevenue"),
  adminDashboardSubscriptionRevenue: document.getElementById("adminDashboardSubscriptionRevenue"),
  adminDashboardAttentionTotal: document.getElementById("adminDashboardAttentionTotal"),
  adminDashboardAttention: document.getElementById("adminDashboardAttention"),
  adminDashboardRecentOrders: document.getElementById("adminDashboardRecentOrders"),
  adminDashboardRecentUsers: document.getElementById("adminDashboardRecentUsers"),
  adminDashboardContent: document.getElementById("adminDashboardContent"),

  adminCategorySelect: document.getElementById("adminCategorySelect"),
  adminCategoryTitle: document.getElementById("adminCategoryTitle"),
  adminCategoryNewBtn: document.getElementById("adminCategoryNewBtn"),
  adminCategorySaveBtn: document.getElementById("adminCategorySaveBtn"),
  adminCategoryDeleteBtn: document.getElementById("adminCategoryDeleteBtn"),

  adminSausageCategorySelect: document.getElementById("adminSausageCategorySelect"),
  adminSausageSelect: document.getElementById("adminSausageSelect"),
  adminSausageName: document.getElementById("adminSausageName"),
  adminSausageNewBtn: document.getElementById("adminSausageNewBtn"),
  adminSausageSaveBtn: document.getElementById("adminSausageSaveBtn"),
  adminSausageDeleteBtn: document.getElementById("adminSausageDeleteBtn"),

  adminHandbookSectionSelect: document.getElementById("adminHandbookSectionSelect"),
  adminHandbookArticleSelect: document.getElementById("adminHandbookArticleSelect"),
  adminHandbookArticleKey: document.getElementById("adminHandbookArticleKey"),
  adminHandbookArticleTitle: document.getElementById("adminHandbookArticleTitle"),
  adminHandbookArticleText: document.getElementById("adminHandbookArticleText"),
  adminHandbookArticleLink: document.getElementById("adminHandbookArticleLink"),
  adminHandbookNewBtn: document.getElementById("adminHandbookNewBtn"),
  adminHandbookSaveBtn: document.getElementById("adminHandbookSaveBtn"),
  adminHandbookDeleteBtn: document.getElementById("adminHandbookDeleteBtn"),

  adminArticleSelect: document.getElementById("adminArticleSelect"),
  adminArticleTitle: document.getElementById("adminArticleTitle"),
  adminArticleSlug: document.getElementById("adminArticleSlug"),
  adminArticleExcerpt: document.getElementById("adminArticleExcerpt"),
  adminArticleCoverFile: document.getElementById("adminArticleCoverFile"),
  adminArticleCoverUploadBtn: document.getElementById("adminArticleCoverUploadBtn"),
  adminArticleCoverEditorBtn: document.getElementById("adminArticleCoverEditorBtn"),
  adminArticleCoverClearBtn: document.getElementById("adminArticleCoverClearBtn"),
  adminArticleCoverPath: document.getElementById("adminArticleCoverPath"),
  adminArticleCoverPreview: document.getElementById("adminArticleCoverPreview"),
  adminArticleCardImageFile: document.getElementById("adminArticleCardImageFile"),
  adminArticleCardImageUploadBtn: document.getElementById("adminArticleCardImageUploadBtn"),
  adminArticleCardImageEditorBtn: document.getElementById("adminArticleCardImageEditorBtn"),
  adminArticleCardImageClearBtn: document.getElementById("adminArticleCardImageClearBtn"),
  adminArticleCardImagePath: document.getElementById("adminArticleCardImagePath"),
  adminArticleCardImagePreview: document.getElementById("adminArticleCardImagePreview"),
  adminArticleToolbar: document.getElementById("adminArticleToolbar"),
  adminArticleBlockFormat: document.getElementById("adminArticleBlockFormat"),
  adminArticleEmojiBtn: document.getElementById("adminArticleEmojiBtn"),
  adminArticleEmojiPanel: document.getElementById("adminArticleEmojiPanel"),
  adminArticleEditor: document.getElementById("adminArticleEditor"),
  adminArticleInlineImageFile: document.getElementById("adminArticleInlineImageFile"),
  adminArticleInlineImageUploadBtn: document.getElementById("adminArticleInlineImageUploadBtn"),
  adminArticleInlineImageEditorBtn: document.getElementById("adminArticleInlineImageEditorBtn"),
  adminArticleLinkBtn: document.getElementById("adminArticleLinkBtn"),
  adminArticlePhotoEditorBtn: document.getElementById("adminArticlePhotoEditorBtn"),
  adminArticlePublished: document.getElementById("adminArticlePublished"),
  adminArticlePremium: document.getElementById("adminArticlePremium"),
  adminArticleNewBtn: document.getElementById("adminArticleNewBtn"),
  adminArticleSaveBtn: document.getElementById("adminArticleSaveBtn"),
  adminArticleDeleteBtn: document.getElementById("adminArticleDeleteBtn"),
  adminArticleResult: document.getElementById("adminArticleResult"),

  articlePhotoEditorDialog: document.getElementById("articlePhotoEditorDialog"),
  photoEditorCanvas: document.getElementById("photoEditorCanvas"),
  photoEditorViewport: document.getElementById("photoEditorViewport"),
  photoEditorCropOverlay: document.getElementById("photoEditorCropOverlay"),
  photoEditorCropFrame: document.getElementById("photoEditorCropFrame"),
  photoEditorCropFrameLabel: document.getElementById("photoEditorCropFrameLabel"),
  photoEditorTargetPreset: document.getElementById("photoEditorTargetPreset"),
  photoEditorToggleCropBtn: document.getElementById("photoEditorToggleCropBtn"),
  photoEditorApplyCropBtn: document.getElementById("photoEditorApplyCropBtn"),
  photoEditorResizeMode: document.getElementById("photoEditorResizeMode"),
  photoEditorResizeModeHint: document.getElementById("photoEditorResizeModeHint"),
  photoEditorZoomField: document.getElementById("photoEditorZoomField"),
  photoEditorZoomSlider: document.getElementById("photoEditorZoomSlider"),
  photoEditorZoomValue: document.getElementById("photoEditorZoomValue"),
  photoEditorBrightness: document.getElementById("photoEditorBrightness"),
  photoEditorBrightnessValue: document.getElementById("photoEditorBrightnessValue"),
  photoEditorContrast: document.getElementById("photoEditorContrast"),
  photoEditorContrastValue: document.getElementById("photoEditorContrastValue"),
  photoEditorSaturation: document.getElementById("photoEditorSaturation"),
  photoEditorSaturationValue: document.getElementById("photoEditorSaturationValue"),
  photoEditorBlur: document.getElementById("photoEditorBlur"),
  photoEditorBlurValue: document.getElementById("photoEditorBlurValue"),
  photoEditorOutputWidth: document.getElementById("photoEditorOutputWidth"),
  photoEditorOutputHeight: document.getElementById("photoEditorOutputHeight"),
  photoEditorOutputFormat: document.getElementById("photoEditorOutputFormat"),
  photoEditorOutputQuality: document.getElementById("photoEditorOutputQuality"),
  photoEditorOutputQualityValue: document.getElementById("photoEditorOutputQualityValue"),
  photoEditorQualityField: document.getElementById("photoEditorQualityField"),
  photoEditorOutputHint: document.getElementById("photoEditorOutputHint"),
  photoEditorRotateCcwBtn: document.getElementById("photoEditorRotateCcwBtn"),
  photoEditorRotateCwBtn: document.getElementById("photoEditorRotateCwBtn"),
  photoEditorFlipHBtn: document.getElementById("photoEditorFlipHBtn"),
  photoEditorFlipVBtn: document.getElementById("photoEditorFlipVBtn"),
  photoEditorResetBtn: document.getElementById("photoEditorResetBtn"),
  photoEditorSaveBtn: document.getElementById("photoEditorSaveBtn"),

  adminRecipeCategorySelect: document.getElementById("adminRecipeCategorySelect"),
  adminRecipeSausageSelect: document.getElementById("adminRecipeSausageSelect"),
  adminRecipeName: document.getElementById("adminRecipeName"),
  adminRecipeSlug: document.getElementById("adminRecipeSlug"),
  adminRecipeText: document.getElementById("adminRecipeText"),
  adminRecipePhotoPath: document.getElementById("adminRecipePhotoPath"),
  adminRecipePhotoId: document.getElementById("adminRecipePhotoId"),
  adminRecipePhotoFile: document.getElementById("adminRecipePhotoFile"),
  adminRecipeUploadBtn: document.getElementById("adminRecipeUploadBtn"),
  adminRecipePhotoUploadHint: document.getElementById("adminRecipePhotoUploadHint"),
  adminRecipePhotoPreview: document.getElementById("adminRecipePhotoPreview"),
  adminIngredientsList: document.getElementById("adminIngredientsList"),
  adminAddIngredientRow: document.getElementById("adminAddIngredientRow"),
  adminSaveRecipeBtn: document.getElementById("adminSaveRecipeBtn"),
  adminClearRecipeTextBtn: document.getElementById("adminClearRecipeTextBtn"),
  adminClearRecipePhotoBtn: document.getElementById("adminClearRecipePhotoBtn"),

  adminFaqSelect: document.getElementById("adminFaqSelect"),
  adminFaqQuestion: document.getElementById("adminFaqQuestion"),
  adminFaqAnswer: document.getElementById("adminFaqAnswer"),
  adminFaqNewBtn: document.getElementById("adminFaqNewBtn"),
  adminFaqSaveBtn: document.getElementById("adminFaqSaveBtn"),
  adminFaqDeleteBtn: document.getElementById("adminFaqDeleteBtn"),

  adminEquipmentSelect: document.getElementById("adminEquipmentSelect"),
  adminEquipmentCategoryName: document.getElementById("adminEquipmentCategoryName"),
  adminEquipmentTitle: document.getElementById("adminEquipmentTitle"),
  adminEquipmentDescription: document.getElementById("adminEquipmentDescription"),
  adminEquipmentBuyLink: document.getElementById("adminEquipmentBuyLink"),
  adminEquipmentBuyLabel: document.getElementById("adminEquipmentBuyLabel"),
  adminEquipmentPhotoPath: document.getElementById("adminEquipmentPhotoPath"),
  adminEquipmentPhotoId: document.getElementById("adminEquipmentPhotoId"),
  adminEquipmentPhotoFile: document.getElementById("adminEquipmentPhotoFile"),
  adminEquipmentUploadBtn: document.getElementById("adminEquipmentUploadBtn"),
  adminEquipmentPhotoUploadHint: document.getElementById("adminEquipmentPhotoUploadHint"),
  adminEquipmentSortOrder: document.getElementById("adminEquipmentSortOrder"),
  adminEquipmentActive: document.getElementById("adminEquipmentActive"),
  adminEquipmentNewBtn: document.getElementById("adminEquipmentNewBtn"),
  adminEquipmentSaveBtn: document.getElementById("adminEquipmentSaveBtn"),
  adminEquipmentDeleteBtn: document.getElementById("adminEquipmentDeleteBtn"),
  adminStoreImportFile: document.getElementById("adminStoreImportFile"),
  adminStoreImportBtn: document.getElementById("adminStoreImportBtn"),
  adminStoreNewBtn: document.getElementById("adminStoreNewBtn"),
  adminStoreSaveBtn: document.getElementById("adminStoreSaveBtn"),
  adminStoreDeleteBtn: document.getElementById("adminStoreDeleteBtn"),
  adminOrdersCurrentBtn: document.getElementById("adminOrdersCurrentBtn"),
  adminOrdersCompletedBtn: document.getElementById("adminOrdersCompletedBtn"),
  adminOrdersFailedBtn: document.getElementById("adminOrdersFailedBtn"),
  adminOrdersDigitalBtn: document.getElementById("adminOrdersDigitalBtn"),
  adminOrdersRefreshBtn: document.getElementById("adminOrdersRefreshBtn"),
  adminDigitalBookManager: document.getElementById("adminDigitalBookManager"),
  adminDigitalBookStatus: document.getElementById("adminDigitalBookStatus"),
  adminDigitalBookFile: document.getElementById("adminDigitalBookFile"),
  adminDigitalBookUploadBtn: document.getElementById("adminDigitalBookUploadBtn"),
  adminDigitalBookPreviewBtn: document.getElementById("adminDigitalBookPreviewBtn"),
  adminMobileBookStatus: document.getElementById("adminMobileBookStatus"),
  adminMobileBookFile: document.getElementById("adminMobileBookFile"),
  adminMobileBookUploadBtn: document.getElementById("adminMobileBookUploadBtn"),
  adminMobileBookPreviewBtn: document.getElementById("adminMobileBookPreviewBtn"),
  adminDigitalOrderTabs: document.getElementById("adminDigitalOrderTabs"),
  adminDigitalDeliveredBtn: document.getElementById("adminDigitalDeliveredBtn"),
  adminDigitalAttentionBtn: document.getElementById("adminDigitalAttentionBtn"),
  adminDigitalSummary: document.getElementById("adminDigitalSummary"),
  adminOrdersResult: document.getElementById("adminOrdersResult"),
  adminOrdersList: document.getElementById("adminOrdersList"),
  adminCartsRefreshBtn: document.getElementById("adminCartsRefreshBtn"),
  adminCartsSummary: document.getElementById("adminCartsSummary"),
  adminCartsList: document.getElementById("adminCartsList"),

  adminReloadTickets: document.getElementById("adminReloadTickets"),
  adminTicketSelect: document.getElementById("adminTicketSelect"),
  adminTicketDetails: document.getElementById("adminTicketDetails"),
  adminTicketAnswer: document.getElementById("adminTicketAnswer"),
  adminSendTicketAnswer: document.getElementById("adminSendTicketAnswer"),

  adminNotificationGlobalTab: document.getElementById("adminNotificationGlobalTab"),
  adminNotificationPersonalTab: document.getElementById("adminNotificationPersonalTab"),
  adminNotificationGlobalPanel: document.getElementById("adminNotificationGlobalPanel"),
  adminNotificationPersonalPanel: document.getElementById("adminNotificationPersonalPanel"),
  adminNotificationGlobalTitle: document.getElementById("adminNotificationGlobalTitle"),
  adminNotificationGlobalMessage: document.getElementById("adminNotificationGlobalMessage"),
  adminNotificationGlobalSendBtn: document.getElementById("adminNotificationGlobalSendBtn"),
  adminNotificationUserSelect: document.getElementById("adminNotificationUserSelect"),
  adminNotificationPersonalTitle: document.getElementById("adminNotificationPersonalTitle"),
  adminNotificationPersonalMessage: document.getElementById("adminNotificationPersonalMessage"),
  adminNotificationPersonalSendBtn: document.getElementById("adminNotificationPersonalSendBtn"),
  adminNotificationsResult: document.getElementById("adminNotificationsResult"),
  adminNotificationsRefreshBtn: document.getElementById("adminNotificationsRefreshBtn"),
  adminNotificationsHistory: document.getElementById("adminNotificationsHistory"),

  adminUsersSelect: document.getElementById("adminUsersSelect"),
  adminUsersInfo: document.getElementById("adminUsersInfo"),
  adminUserProfile: document.getElementById("adminUserProfile"),
  adminUserProfileDossier: document.getElementById("adminUserProfileDossier"),
  adminUserDossier: document.getElementById("adminUserDossier"),
  adminUserIsAdmin: document.getElementById("adminUserIsAdmin"),
  adminUserIsActive: document.getElementById("adminUserIsActive"),
  adminUserStoreSausages: document.getElementById("adminUserStoreSausages"),
  adminUserUsername: document.getElementById("adminUserUsername"),
  adminUserPassword: document.getElementById("adminUserPassword"),
  adminNewAdminUsername: document.getElementById("adminNewAdminUsername"),
  adminNewAdminPassword: document.getElementById("adminNewAdminPassword"),
  adminUserAccessTier: document.getElementById("adminUserAccessTier"),
  adminUserAccessHours: document.getElementById("adminUserAccessHours"),
  adminUserAccessMode: document.getElementById("adminUserAccessMode"),
  adminApplyUserAccessBtn: document.getElementById("adminApplyUserAccessBtn"),
  adminRevokeAccessBtn: document.getElementById("adminRevokeAccessBtn"),
  adminDisableUserAutopayBtn: document.getElementById("adminDisableUserAutopayBtn"),
  adminBanUserBtn: document.getElementById("adminBanUserBtn"),
  adminUnbanUserBtn: document.getElementById("adminUnbanUserBtn"),
  adminRevokeUserSessionsBtn: document.getElementById("adminRevokeUserSessionsBtn"),
  adminSaveUserBtn: document.getElementById("adminSaveUserBtn"),
  adminGrantAdminBtn: document.getElementById("adminGrantAdminBtn"),
  adminRevokeAdminBtn: document.getElementById("adminRevokeAdminBtn"),
  adminRunUserRenewalBtn: document.getElementById("adminRunUserRenewalBtn"),
  adminResetUser2faBtn: document.getElementById("adminResetUser2faBtn"),
  adminRefreshAdminsBtn: document.getElementById("adminRefreshAdminsBtn"),
  adminDbPresets: document.getElementById("adminDbPresets"),
  adminDbSourceFilter: document.getElementById("adminDbSourceFilter"),
  adminDbLevelFilter: document.getElementById("adminDbLevelFilter"),
  adminDbUserIdFilter: document.getElementById("adminDbUserIdFilter"),
  adminDbSearchFilter: document.getElementById("adminDbSearchFilter"),
  adminDbLimitFilter: document.getElementById("adminDbLimitFilter"),
  adminDbApplyBtn: document.getElementById("adminDbApplyBtn"),
  adminDbResetBtn: document.getElementById("adminDbResetBtn"),
  adminDbLoadDossierBtn: document.getElementById("adminDbLoadDossierBtn"),
  adminDbCopyBtn: document.getElementById("adminDbCopyBtn"),
  adminDbAutoRefresh: document.getElementById("adminDbAutoRefresh"),
  adminDbPrevBtn: document.getElementById("adminDbPrevBtn"),
  adminDbNextBtn: document.getElementById("adminDbNextBtn"),
  adminDbMeta: document.getElementById("adminDbMeta"),
  adminDbLogList: document.getElementById("adminDbLogList"),

  adminAuditMethod: document.getElementById("adminAuditMethod"),
  adminAuditStatusFrom: document.getElementById("adminAuditStatusFrom"),
  adminAuditStatusTo: document.getElementById("adminAuditStatusTo"),
  adminAuditPath: document.getElementById("adminAuditPath"),
  adminAuditIp: document.getElementById("adminAuditIp"),
  adminAuditAdminUserId: document.getElementById("adminAuditAdminUserId"),
  adminAuditRequestId: document.getElementById("adminAuditRequestId"),
  adminAuditCreatedFrom: document.getElementById("adminAuditCreatedFrom"),
  adminAuditCreatedTo: document.getElementById("adminAuditCreatedTo"),
  adminAuditApplyBtn: document.getElementById("adminAuditApplyBtn"),
  adminAuditResetBtn: document.getElementById("adminAuditResetBtn"),
  adminAuditPrevBtn: document.getElementById("adminAuditPrevBtn"),
  adminAuditNextBtn: document.getElementById("adminAuditNextBtn"),
  adminAuditMeta: document.getElementById("adminAuditMeta"),
  adminAuditList: document.getElementById("adminAuditList"),

  adminStatsScope: document.getElementById("adminStatsScope"),
  adminMarketSyncToken: document.getElementById("adminMarketSyncToken"),
  adminRefreshStats: document.getElementById("adminRefreshStats"),
  adminExportStats: document.getElementById("adminExportStats"),
  adminMarketSyncBtn: document.getElementById("adminMarketSyncBtn"),
  adminStatsSummary: document.getElementById("adminStatsSummary"),
  adminHeroImageFile: document.getElementById("adminHeroImageFile"),
  adminUploadHeroImageBtn: document.getElementById("adminUploadHeroImageBtn"),
  adminHeroImageResult: document.getElementById("adminHeroImageResult"),
  adminFaviconFile: document.getElementById("adminFaviconFile"),
  adminUploadFaviconBtn: document.getElementById("adminUploadFaviconBtn"),
  adminFaviconResult: document.getElementById("adminFaviconResult"),
  adminSocialLinkInputs: Array.from(document.querySelectorAll("[data-social-input]")),
  adminSocialLinksSaveBtn: document.getElementById("adminSocialLinksSaveBtn"),
  adminSocialLinksResult: document.getElementById("adminSocialLinksResult"),
  adminPlanSettings: document.getElementById("adminPlanSettings"),
  adminMaintenanceForced: document.getElementById("adminMaintenanceForced"),
  adminMaintenanceStatus: document.getElementById("adminMaintenanceStatus"),
  adminMaintenanceSaveBtn: document.getElementById("adminMaintenanceSaveBtn"),
  adminMaintenanceResult: document.getElementById("adminMaintenanceResult"),
  adminRegistrationBonusEnabled: document.getElementById("adminRegistrationBonusEnabled"),
  adminRegistrationBonusAmount: document.getElementById("adminRegistrationBonusAmount"),
  adminRegistrationBonusSaveBtn: document.getElementById("adminRegistrationBonusSaveBtn"),
  adminRegistrationBonusResult: document.getElementById("adminRegistrationBonusResult"),
  adminPromoCodeInput: document.getElementById("adminPromoCodeInput"),
  adminPromoTierSelect: document.getElementById("adminPromoTierSelect"),
  adminPromoDiscountInput: document.getElementById("adminPromoDiscountInput"),
  adminCreatePromoBtn: document.getElementById("adminCreatePromoBtn"),
  adminPromoList: document.getElementById("adminPromoList"),
  adminFaqExcelFile: document.getElementById("adminFaqExcelFile"),
  adminImportFaqBtn: document.getElementById("adminImportFaqBtn"),
  adminFullExcelFile: document.getElementById("adminFullExcelFile"),
  adminImportFullBtn: document.getElementById("adminImportFullBtn"),
  adminDbMigrationSource: document.getElementById("adminDbMigrationSource"),
  adminDbMigrationTruncate: document.getElementById("adminDbMigrationTruncate"),
  adminRunDbMigrationBtn: document.getElementById("adminRunDbMigrationBtn"),
  adminDbMigrationResult: document.getElementById("adminDbMigrationResult"),

  adminCatalogResult: document.getElementById("adminCatalogResult"),
  adminHandbookResult: document.getElementById("adminHandbookResult"),
  adminRecipeResult: document.getElementById("adminRecipeResult"),
  adminFaqResult: document.getElementById("adminFaqResult"),
  adminEquipmentResult: document.getElementById("adminEquipmentResult"),
  adminAdminsResult: document.getElementById("adminAdminsResult"),
  adminSupportResult: document.getElementById("adminSupportResult"),
  adminImportResult: document.getElementById("adminImportResult"),
};

const pageMode = String(document.body?.dataset?.mode || "main").toLowerCase();
const isAdminPage = pageMode === "admin";
const COOKIE_NOTICE_KEY = "kolbabot_cookie_notice_v1";
const LAST_PAYMENT_STORAGE_KEY = "kolbabot.miniapp.last_payment";
const PENDING_CARD_BIND_STORAGE_KEY = "kolbabot.miniapp.pending_add_card";
const ADMIN_DB_LOG_FILTERS_KEY = "kolbabot.admin.db.log.filters.v1";
const MAX_PENDING_PAYMENT_AGE_MS = 24 * 60 * 60 * 1000;
const MAINTENANCE_POLL_INTERVAL_MS = 5000;
const APP_TIME_ZONE = "Europe/Moscow";
const MOSCOW_OFFSET_MINUTES = 3 * 60;
const MSK_DATE_TIME_FORMATTER = new Intl.DateTimeFormat("ru-RU", {
  timeZone: APP_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
});

function rememberPendingPayment(orderId, tier, userId) {
  const safeOrderId = String(orderId || "").trim();
  if (!safeOrderId || !window.localStorage) return;
  try {
    const payload = {
      order_id: safeOrderId,
      tier: String(tier || "").trim().toLowerCase(),
      user_id: Number(userId || 0) || 0,
      created_at_ms: Date.now(),
    };
    window.localStorage.setItem(LAST_PAYMENT_STORAGE_KEY, JSON.stringify(payload));
  } catch (_error) {
    // ignore storage errors
  }
}

function readPendingPayment() {
  if (!window.localStorage) return null;
  try {
    const raw = window.localStorage.getItem(LAST_PAYMENT_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    const safeOrderId = String(parsed?.order_id || "").trim();
    if (!safeOrderId) return null;
    const createdAtMs = Number(parsed?.created_at_ms || 0);
    if (createdAtMs > 0 && Date.now() - createdAtMs > MAX_PENDING_PAYMENT_AGE_MS) {
      window.localStorage.removeItem(LAST_PAYMENT_STORAGE_KEY);
      return null;
    }
    return {
      order_id: safeOrderId,
      tier: String(parsed?.tier || "").trim().toLowerCase(),
      user_id: Number(parsed?.user_id || 0) || 0,
      created_at_ms: createdAtMs,
    };
  } catch (_error) {
    return null;
  }
}

function clearPendingPayment() {
  if (!window.localStorage) return;
  try {
    window.localStorage.removeItem(LAST_PAYMENT_STORAGE_KEY);
  } catch (_error) {
    // ignore storage errors
  }
}

function rememberPendingCardBind(payload) {
  if (!window.localStorage) return;
  try {
    window.localStorage.setItem(PENDING_CARD_BIND_STORAGE_KEY, JSON.stringify(payload || {}));
  } catch (_error) {
    // ignore storage errors
  }
}

function readPendingCardBind() {
  if (!window.localStorage) return null;
  try {
    const raw = window.localStorage.getItem(PENDING_CARD_BIND_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    const requestKey = String(parsed?.request_key || "").trim();
    if (!requestKey) return null;
    const createdAtMs = Number(parsed?.created_at_ms || 0);
    if (createdAtMs > 0 && Date.now() - createdAtMs > MAX_PENDING_PAYMENT_AGE_MS) {
      window.localStorage.removeItem(PENDING_CARD_BIND_STORAGE_KEY);
      return null;
    }
    return {
      request_key: requestKey,
      tier: String(parsed?.tier || "").trim().toLowerCase(),
      promo_code: String(parsed?.promo_code || "").trim().toUpperCase(),
      user_id: Number(parsed?.user_id || 0) || 0,
      consent: parsed?.consent && typeof parsed.consent === "object" ? parsed.consent : null,
      created_at_ms: createdAtMs,
    };
  } catch (_error) {
    return null;
  }
}

function clearPendingCardBind() {
  if (!window.localStorage) return;
  try {
    window.localStorage.removeItem(PENDING_CARD_BIND_STORAGE_KEY);
  } catch (_error) {
    // ignore storage errors
  }
}

const cyrMap = {
  а: "a", б: "b", в: "v", г: "g", д: "d", е: "e", ё: "e", ж: "zh", з: "z", и: "i", й: "y", к: "k",
  л: "l", м: "m", н: "n", о: "o", п: "p", р: "r", с: "s", т: "t", у: "u", ф: "f", х: "h", ц: "ts",
  ч: "ch", ш: "sh", щ: "sch", ъ: "", ы: "y", ь: "", э: "e", ю: "yu", я: "ya",
};

function toSlug(value) {
  const lower = String(value || "").trim().toLowerCase();
  const translit = Array.from(lower).map((ch) => cyrMap[ch] ?? ch).join("");
  return translit
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 64);
}

function uniqueSlug(base, existing) {
  let candidate = base || "item";
  let idx = 2;
  while (existing.has(candidate)) {
    candidate = `${base}-${idx}`;
    idx += 1;
  }
  return candidate;
}

function defaultTierTitle(rawTier) {
  const tier = String(rawTier || "").trim().toLowerCase();
  if (tier === "premium") return "PREMIUM";
  return "Без подписки";
}

function getPlanByTier(rawTier) {
  const tier = String(rawTier || "").trim().toLowerCase();
  if (!tier) return null;
  const plans = Array.isArray(state.payments?.plans) ? state.payments.plans : [];
  return plans.find((item) => String(item?.tier || "").trim().toLowerCase() === tier) || null;
}

function resolveTierTitle(rawTier) {
  const plan = getPlanByTier(rawTier);
  const title = String(plan?.title || "").trim();
  return title || defaultTierTitle(rawTier);
}

function tierTitle(access) {
  if (!access) return "—";
  if (access.is_admin) return "ADMIN";
  const raw = String(access.access_tier || access.paid_tier || access.donut_tier || "none").toLowerCase();
  if (raw === "premium") {
    return resolveTierTitle(raw);
  }
  if (access.is_member) return "Базовый уровень";
  return "Без доступа";
}

function tierRank(rawTier) {
  const tier = String(rawTier || "none").trim().toLowerCase();
  if (tier === "premium") return 1;
  return 0;
}

function parseStorageDateTime(value) {
  const text = String(value || "").trim();
  if (!text) return null;
  const explicitZone = /(?:z|[+-]\d{2}(?::?\d{2})?)$/i.test(text);
  if (explicitZone) {
    const normalized = text
      .replace(" ", "T")
      .replace(/\.(\d{3})\d+/, ".$1")
      .replace(/([+-]\d{2})$/, "$1:00")
      .replace(/([+-]\d{2})(\d{2})$/, "$1:$2");
    const explicitDate = new Date(normalized);
    return Number.isNaN(explicitDate.getTime()) ? null : explicitDate;
  }

  // Naive API timestamps are app wall-clock time: Europe/Moscow.
  const match = text.match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?/);
  if (!match) {
    const fallback = new Date(text);
    return Number.isNaN(fallback.getTime()) ? null : fallback;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const hour = Number(match[4]);
  const minute = Number(match[5]);
  const second = Number(match[6] || 0);
  const dt = new Date(Date.UTC(year, month - 1, day, hour, minute - MOSCOW_OFFSET_MINUTES, second));
  if (Number.isNaN(dt.getTime())) return null;
  return dt;
}

function formatMskDateTimeLabel(value, fallback = "") {
  const dt = parseStorageDateTime(value);
  if (!dt) return fallback;
  return `${MSK_DATE_TIME_FORMATTER.format(dt).replace(",", "")} МСК`;
}

function looksLikeDateTime(value) {
  return /^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}/.test(String(value || "").trim());
}

function escapeHtml(value) {
  return String(value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

/** Заголовок тарифа: зачёркнутый list price и новая цена (числа — из API). */
function planPriceHeadlineHtml(tierTitleEscaped, baseRub, saleRub, durationDays) {
  const b = Math.round(Number(baseRub) || 0);
  const s = Math.round(Number(saleRub) || 0);
  const d = Math.round(Number(durationDays) || 30);
  if (b > s) {
    return `${tierTitleEscaped} • <span class="plan-price"><del class="plan-price__old">${b}</del> <strong class="plan-price__new">${s}</strong></span> <span class="plan-price__currency">₽</span> / ${d} дней`;
  }
  return `${tierTitleEscaped} • <strong class="plan-price__plain">${s}</strong> <span class="plan-price__currency">₽</span> / ${d} дней`;
}

/** Подпись: промокод только на первый период, далее цена тарифа. */
function planPromoFirstPeriodFootnoteHtml(firstRub, regularRub, durationDays) {
  const f = Math.round(Number(firstRub) || 0);
  const r = Math.round(Number(regularRub) || 0);
  const d = Math.round(Number(durationDays) || 30);
  if (f >= r) return "";
  return `<small class="plan-card__promo-note">Промокод снижает оплату только за первый период (${d} дн.). Далее автопродление — <strong>${r}</strong> ₽ за ${d} дней.</small>`;
}

function planPaywallTitleWithPromoHtml(opts) {
  const { tierTitleEscaped, baseRub, firstRub, regularRub, durationDays } = opts;
  const head = planPriceHeadlineHtml(tierTitleEscaped, baseRub, firstRub, durationDays);
  const foot = planPromoFirstPeriodFootnoteHtml(firstRub, regularRub, durationDays);
  if (!foot) return head;
  return `${head}<br>${foot}`;
}

function setHtml(target, html) {
  if (!target) return;
  // eslint-disable-next-line no-param-reassign
  target.innerHTML = String(html ?? "");
}

function formatNotificationMessage(value) {
  return escapeHtml(value)
    .replace(/\*\*([^*\n]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\r?\n/g, "<br>");
}

function setResult(target, text, isError = false) {
  if (!target) return;
  target.textContent = text;
  target.classList.toggle("error-text", Boolean(isError));
  if (isError && text) {
    showToast(text, true);
  }
}

function clearErrorTextState(target) {
  if (!target) return;
  target.classList.remove("error-text");
}

function setGlobalLoading(visible) {
  if (!el.globalLoading) return;
  el.globalLoading.classList.toggle("hidden", !visible);
}

function showToast(message, isError = false) {
  if (!el.appToast || !message) return;
  el.appToast.textContent = String(message);
  el.appToast.classList.toggle("error", Boolean(isError));
  el.appToast.classList.remove("hidden");
  if (state.toastTimer) {
    clearTimeout(state.toastTimer);
  }
  state.toastTimer = setTimeout(() => {
    el.appToast.classList.add("hidden");
    el.appToast.classList.remove("error");
    el.appToast.textContent = "";
  }, 2800);
}

const NOTIFICATION_PERMISSION_DISMISSED_KEY = "kolbabot.notifications.web-push-dismissed-v2";

function desktopNotificationsSupported() {
  return typeof window !== "undefined" && "Notification" in window;
}

function pushNotificationsSupported() {
  return desktopNotificationsSupported()
    && typeof navigator !== "undefined"
    && "serviceWorker" in navigator
    && "PushManager" in window;
}

function notificationPermissionPromptDismissed() {
  try {
    return window.sessionStorage.getItem(NOTIFICATION_PERMISSION_DISMISSED_KEY) === "1";
  } catch (_error) {
    return false;
  }
}

function setNotificationPermissionPromptDismissed(dismissed) {
  try {
    if (dismissed) {
      window.sessionStorage.setItem(NOTIFICATION_PERMISSION_DISMISSED_KEY, "1");
    } else {
      window.sessionStorage.removeItem(NOTIFICATION_PERMISSION_DISMISSED_KEY);
    }
  } catch (_error) {
    // В закрытом режиме браузер может запрещать доступ к sessionStorage.
  }
}

function syncNotificationPermissionPrompt() {
  if (!el.notificationPermissionPrompt) return;
  const permission = desktopNotificationsSupported() ? window.Notification.permission : "denied";
  const needsSetup = permission === "default" || Boolean(
    permission === "granted"
    && pushNotificationsSupported()
    && state.notifications.pushSyncAttempted
    && !state.notifications.pushSubscriptionActive
  );
  const shouldShow = Boolean(
    state.isAuthenticated
    && desktopNotificationsSupported()
    && needsSetup
    && !notificationPermissionPromptDismissed()
  );
  el.notificationPermissionPrompt.classList.toggle("hidden", !shouldShow);
}

function urlBase64ToUint8Array(value) {
  const normalized = String(value || "").replace(/-/g, "+").replace(/_/g, "/");
  const padding = "=".repeat((4 - (normalized.length % 4)) % 4);
  const binary = window.atob(normalized + padding);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function arrayBufferToUrlBase64(value) {
  if (!value) return "";
  const bytes = new Uint8Array(value);
  let binary = "";
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return window.btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function registerNotificationServiceWorker() {
  if (!pushNotificationsSupported()) return null;
  if (state.notifications.serviceWorkerRegistration) {
    return state.notifications.serviceWorkerRegistration;
  }
  if (!state.notifications.serviceWorkerPromise) {
    state.notifications.serviceWorkerPromise = navigator.serviceWorker.register(
      "/service-worker.js",
      { scope: "/", updateViaCache: "none" },
    ).then(async () => {
      const registration = await navigator.serviceWorker.ready;
      state.notifications.serviceWorkerRegistration = registration;
      return registration;
    }).catch((error) => {
      state.notifications.serviceWorkerPromise = null;
      throw error;
    });
  }
  return state.notifications.serviceWorkerPromise;
}

async function ensurePushSubscription({ subscribeIfMissing = false } = {}) {
  state.notifications.pushSyncAttempted = true;
  if (
    !state.isAuthenticated
    || !pushNotificationsSupported()
    || window.Notification.permission !== "granted"
  ) {
    state.notifications.pushSubscriptionActive = false;
    syncNotificationPermissionPrompt();
    return false;
  }
  const registration = await registerNotificationServiceWorker();
  const config = await api("/api/push/config", { skipAuthRedirect: true });
  const publicKey = String(config?.public_key || "").trim();
  if (!registration || !config?.enabled || !publicKey) {
    throw new Error("Сервер Push API пока не настроен.");
  }
  let subscription = await registration.pushManager.getSubscription();
  const currentApplicationKey = arrayBufferToUrlBase64(subscription?.options?.applicationServerKey);
  if (subscription && currentApplicationKey && currentApplicationKey !== publicKey) {
    await subscription.unsubscribe();
    subscription = null;
  }
  if (!subscription && subscribeIfMissing) {
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    });
  }
  if (!subscription) {
    state.notifications.pushSubscriptionActive = false;
    syncNotificationPermissionPrompt();
    return false;
  }
  await api("/api/push/subscriptions", {
    method: "POST",
    body: { subscription: subscription.toJSON(), content_encoding: "aes128gcm" },
    skipAuthRedirect: true,
  });
  state.notifications.pushSubscriptionActive = true;
  setNotificationPermissionPromptDismissed(false);
  syncNotificationPermissionPrompt();
  return true;
}

async function unsubscribePushNotifications() {
  if (!pushNotificationsSupported()) return;
  try {
    const registration = await navigator.serviceWorker.getRegistration("/");
    const subscription = await registration?.pushManager?.getSubscription();
    if (!subscription) return;
    try {
      await api("/api/push/subscriptions", {
        method: "DELETE",
        body: { endpoint: subscription.endpoint },
        skipAuthRedirect: true,
      });
    } finally {
      await subscription.unsubscribe();
    }
  } finally {
    state.notifications.pushSubscriptionActive = false;
  }
}

function getNotificationAudioContext() {
  if (state.notifications.audioContext) return state.notifications.audioContext;
  const AudioContextClass = window.AudioContext || window.webkitAudioContext;
  if (typeof AudioContextClass !== "function") return null;
  try {
    state.notifications.audioContext = new AudioContextClass();
  } catch (_error) {
    state.notifications.audioContext = null;
  }
  return state.notifications.audioContext;
}

async function unlockNotificationAudio() {
  const audioContext = getNotificationAudioContext();
  if (!audioContext) return false;
  if (audioContext.state === "suspended") {
    try {
      await audioContext.resume();
    } catch (_error) {
      // Звук останется недоступен, если браузер запретил его до следующего действия пользователя.
    }
  }
  return audioContext.state === "running";
}

function bindNotificationAudioUnlock() {
  let unlocking = false;
  const unlock = () => {
    if (unlocking) return;
    unlocking = true;
    void unlockNotificationAudio().then((unlocked) => {
      if (!unlocked) return;
      document.removeEventListener("pointerdown", unlock, true);
      document.removeEventListener("keydown", unlock, true);
    }).finally(() => {
      unlocking = false;
    });
  };
  document.addEventListener("pointerdown", unlock, true);
  document.addEventListener("keydown", unlock, true);
}

function playNotificationSound() {
  const audioContext = getNotificationAudioContext();
  if (!audioContext || audioContext.state !== "running") return false;
  const play = () => {
    const startedAt = audioContext.currentTime;
    const oscillator = audioContext.createOscillator();
    const gain = audioContext.createGain();
    oscillator.type = "sine";
    oscillator.frequency.setValueAtTime(784, startedAt);
    oscillator.frequency.setValueAtTime(1046.5, startedAt + 0.16);
    gain.gain.setValueAtTime(0.0001, startedAt);
    gain.gain.exponentialRampToValueAtTime(0.12, startedAt + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, startedAt + 0.42);
    oscillator.connect(gain);
    gain.connect(audioContext.destination);
    oscillator.start(startedAt);
    oscillator.stop(startedAt + 0.43);
  };
  play();
  return true;
}

function plainNotificationText(value) {
  return String(value || "")
    .replace(/\*\*([^*\n]+)\*\*/g, "$1")
    .replace(/\s+/g, " ")
    .trim();
}

async function showDesktopNotification(item, customSoundPlaying = false) {
  if (!desktopNotificationsSupported() || window.Notification.permission !== "granted") return;
  const notificationId = Number(item?.id || 0);
  const title = String(item?.title || "Новое уведомление");
  const actionUrl = safeActionUrl(item?.action_url) || "/";
  const options = {
    body: plainNotificationText(item?.message),
    icon: "/favicon.ico",
    badge: "/favicon.ico",
    tag: String(item?.tag || (notificationId > 0
      ? `kolbabot-notification-${notificationId}`
      : "kolbabot-notification")),
    renotify: false,
    silent: Boolean(customSoundPlaying),
    vibrate: [180, 80, 180],
    data: { url: actionUrl, notificationId },
  };

  // На Windows обычный Notification надёжнее поднимает системный toast.
  // Android запрещает этот конструктор — там сразу сработает fallback через service worker.
  try {
    const desktopNotification = new Notification(title, options);
    desktopNotification.onclick = () => {
      window.focus();
      window.location.assign(actionUrl);
      desktopNotification.close();
    };
    return;
  } catch (_constructorError) {
    // Продолжаем через service worker для Android и браузеров без конструктора Notification.
  }

  try {
    const registration = await registerNotificationServiceWorker();
    if (registration) {
      await registration.showNotification(title, options);
    }
  } catch (_error) {
    // Встроенные уведомления и счётчик продолжат работать, даже если ОС отклонила всплывающее окно.
  }
}

function announceNewNotifications(items) {
  const notifications = Array.isArray(items) ? items : [];
  if (!notifications.length) return;
  const customSoundPlaying = playNotificationSound();
  if (
    desktopNotificationsSupported()
    && window.Notification.permission === "granted"
    && !state.notifications.pushSubscriptionActive
  ) {
    // При активной push-подписке системное уведомление уже показывает service worker.
    // Локальный Notification нужен только как fallback, иначе Opera/Windows покажет дубль.
    notifications.forEach((item) => { void showDesktopNotification(item, customSoundPlaying); });
  }
}

async function requestDesktopNotificationPermission() {
  if (!desktopNotificationsSupported()) {
    showToast("Этот браузер не поддерживает уведомления на ПК.", true);
    return;
  }
  setButtonBusy(el.notificationPermissionEnableBtn, true, "Ожидаем…");
  try {
    const permissionPromise = window.Notification.permission === "default"
      ? window.Notification.requestPermission()
      : Promise.resolve(window.Notification.permission);
    await unlockNotificationAudio();
    const permission = await permissionPromise;
    if (permission === "granted") {
      const subscribed = pushNotificationsSupported()
        ? await ensurePushSubscription({ subscribeIfMissing: true })
        : true;
      if (!subscribed) throw new Error("Не удалось создать push-подписку.");
      const customSoundPlaying = playNotificationSound();
      await showDesktopNotification({
        title: "Push-уведомления включены",
        message: "Теперь уведомления будут приходить даже при закрытой вкладке.",
        action_url: "/",
        tag: "kolbabot-push-ready",
      }, customSoundPlaying);
      setNotificationPermissionPromptDismissed(false);
      syncNotificationPermissionPrompt();
      showToast("Push-уведомления подключены.");
      return;
    }
    setNotificationPermissionPromptDismissed(true);
    syncNotificationPermissionPrompt();
    showToast(
      permission === "denied"
        ? "Уведомления заблокированы в настройках браузера."
        : "Push-уведомления пока не включены.",
      permission === "denied",
    );
  } catch (error) {
    showToast(errorText(error, "Не удалось запросить разрешение на уведомления."), true);
  } finally {
    setButtonBusy(el.notificationPermissionEnableBtn, false);
  }
}

function updateNotificationsBadge(count = state.notifications.unreadCount) {
  const safeCount = Math.max(0, Number(count || 0));
  state.notifications.unreadCount = safeCount;
  if (!el.notificationsBadge) return;
  el.notificationsBadge.textContent = safeCount > 99 ? "99+" : String(safeCount);
  el.notificationsBadge.classList.toggle("hidden", safeCount <= 0);
}

function positionNotificationsPopover() {
  // Popover is anchored in CSS to the bell wrapper. Kept for existing resize/scroll listeners.
}

function closeNotificationsPopover() {
  if (!el.notificationsPopover || !el.notificationsBtn) return;
  el.notificationsPopover.classList.add("hidden");
  el.notificationsBtn.classList.remove("is-open");
  el.notificationsBtn.setAttribute("aria-expanded", "false");
}

function openNotificationsPopover() {
  if (!state.isAuthenticated || !el.notificationsPopover || !el.notificationsBtn) return;
  positionNotificationsPopover();
  el.notificationsPopover.classList.remove("hidden");
  el.notificationsBtn.classList.add("is-open");
  el.notificationsBtn.setAttribute("aria-expanded", "true");
}

async function toggleNotificationsPopover() {
  if (!state.isAuthenticated || !el.notificationsPopover) return;
  const isOpen = !el.notificationsPopover.classList.contains("hidden");
  if (isOpen) {
    closeNotificationsPopover();
    return;
  }
  if (el.notificationsList) {
    el.notificationsList.textContent = "Загрузка…";
    el.notificationsList.classList.add("muted");
  }
  openNotificationsPopover();
  await loadNotifications({ silent: false });
  positionNotificationsPopover();
}

function renderNotificationsList() {
  if (!el.notificationsList) return;
  const items = Array.isArray(state.notifications.items) ? state.notifications.items : [];
  if (!items.length) {
    el.notificationsList.classList.add("muted");
    const unread = Number(state.notifications.unreadCount || 0);
    el.notificationsList.textContent = state.notifications.loaded
      ? (unread > 0 ? "Обновляю список уведомлений…" : "Уведомлений пока нет.")
      : "Загрузка…";
    if (el.notificationsReadAllBtn) el.notificationsReadAllBtn.disabled = true;
    if (el.notificationsClearBtn) el.notificationsClearBtn.disabled = Number(state.notifications.readCount || 0) <= 0;
    return;
  }

  el.notificationsList.classList.remove("muted");
  setHtml(el.notificationsList, "");
  items.forEach((item) => {
    const notificationId = Number(item.id || 0);
    const hasHighlightedMessage = String(item.message || "").includes("**");
    const row = document.createElement("article");
    row.className = `notification-item${item.is_read ? "" : " is-unread"}${hasHighlightedMessage ? " has-highlighted-message" : ""}${state.notifications.openedId === notificationId ? " is-open" : ""}`;
    row.dataset.notificationId = String(notificationId);
    row.tabIndex = 0;
    row.setAttribute("role", "button");
    setHtml(row, `
      <div class="notification-item__title">
        <span>${escapeHtml(item.title || "Без заголовка")}</span>
        <span class="notification-item__date">${escapeHtml(formatMskDateTimeLabel(item.created_at, ""))}</span>
      </div>
      <div class="notification-item__message">${formatNotificationMessage(item.message || "")}</div>
    `);
    row.addEventListener("click", (event) => {
      event.stopPropagation();
      void openNotificationMessage(notificationId);
    });
    row.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;
      event.preventDefault();
      event.stopPropagation();
      void openNotificationMessage(notificationId);
    });
    el.notificationsList.appendChild(row);
  });
  if (el.notificationsReadAllBtn) {
    el.notificationsReadAllBtn.disabled = Number(state.notifications.unreadCount || 0) <= 0;
  }
  if (el.notificationsClearBtn) {
    el.notificationsClearBtn.disabled = Number(state.notifications.readCount || 0) <= 0;
  }
}

async function loadNotifications({ silent = false } = {}) {
  if (!state.isAuthenticated || !el.notificationsBtn) {
    state.notifications.items = [];
    state.notifications.unreadCount = 0;
    state.notifications.readCount = 0;
    state.notifications.loaded = true;
    updateNotificationsBadge(0);
    renderNotificationsList();
    return;
  }
  if (!silent && el.notificationsList) {
    el.notificationsList.textContent = "Загрузка…";
    el.notificationsList.classList.add("muted");
  }
  try {
    const data = await api("/api/notifications?limit=50", { skipAuthRedirect: true });
    const nextItems = Array.isArray(data.items) ? data.items : [];
    const previousHighestId = Number(state.notifications.highestId || 0);
    const newUnreadItems = state.notifications.loaded
      ? nextItems.filter((item) => {
        const notificationId = Number(item?.id || 0);
        return notificationId > previousHighestId && !item?.is_read;
      })
      : [];
    state.notifications.highestId = nextItems.reduce(
      (highestId, item) => Math.max(highestId, Number(item?.id || 0)),
      previousHighestId,
    );
    state.notifications.items = nextItems;
    state.notifications.unreadCount = Number(data.unread_count || 0);
    state.notifications.readCount = Number(data.read_count || 0);
    state.notifications.loaded = true;
    updateNotificationsBadge(state.notifications.unreadCount);
    renderNotificationsList();
    announceNewNotifications(newUnreadItems);
  } catch (error) {
    if (!silent) {
      setResult(el.notificationsList, errorText(error, "Не удалось загрузить уведомления."), true);
    }
  }
}

async function openNotificationMessage(notificationId) {
  const nid = Number(notificationId || 0);
  if (nid <= 0) return;
  const item = (state.notifications.items || []).find((x) => Number(x.id || 0) === nid);
  if (!item) return;
  if (item && !item.is_read) {
    item.is_read = true;
    state.notifications.readCount = Number(state.notifications.readCount || 0) + 1;
    updateNotificationsBadge(Math.max(0, Number(state.notifications.unreadCount || 0) - 1));
    renderNotificationsList();
    try {
      const data = await api(`/api/notifications/${encodeURIComponent(String(nid))}/read`, {
        method: "POST",
        body: {},
        skipAuthRedirect: true,
      });
      updateNotificationsBadge(Number(data.unread_count || 0));
    } catch (error) {
      await loadNotifications({ silent: true });
      showToast(errorText(error, "Не удалось отметить уведомление прочитанным."), true);
    }
  }
  const actionUrl = safeActionUrl(item.action_url);
  if (actionUrl) {
    closeNotificationsPopover();
    window.location.assign(actionUrl);
    return;
  }
  state.notifications.openedId = state.notifications.openedId === nid ? 0 : nid;
  renderNotificationsList();
}

async function markAllNotificationsRead() {
  if (!state.isAuthenticated || !el.notificationsReadAllBtn) return;
  setButtonBusy(el.notificationsReadAllBtn, true, "Готово...");
  try {
    const data = await api("/api/notifications/read-all", {
      method: "POST",
      body: {},
      skipAuthRedirect: true,
    });
    state.notifications.items = (state.notifications.items || []).map((item) => ({ ...item, is_read: true }));
    state.notifications.readCount = Math.max(Number(state.notifications.readCount || 0), state.notifications.items.length);
    updateNotificationsBadge(Number(data.unread_count || 0));
    renderNotificationsList();
  } catch (error) {
    showToast(errorText(error, "Не удалось отметить уведомления прочитанными."), true);
  } finally {
    setButtonBusy(el.notificationsReadAllBtn, false);
    renderNotificationsList();
  }
}

async function clearReadNotifications() {
  if (!state.isAuthenticated || !el.notificationsClearBtn) return;
  setButtonBusy(el.notificationsClearBtn, true, "Очищаем…");
  try {
    const data = await api("/api/notifications/clear-read", {
      method: "POST",
      body: {},
      skipAuthRedirect: true,
    });
    state.notifications.items = (state.notifications.items || []).filter((item) => !item.is_read);
    state.notifications.readCount = 0;
    state.notifications.openedId = 0;
    updateNotificationsBadge(Number(data.unread_count || 0));
    renderNotificationsList();
  } catch (error) {
    showToast(errorText(error, "Не удалось очистить прочитанные уведомления."), true);
  } finally {
    setButtonBusy(el.notificationsClearBtn, false);
    renderNotificationsList();
  }
}

function startNotificationsPolling() {
  if (state.notifications.refreshTimer) {
    clearInterval(state.notifications.refreshTimer);
    state.notifications.refreshTimer = null;
  }
  if (state.notifications.eventSource) {
    state.notifications.eventSource.close();
    state.notifications.eventSource = null;
  }
  if (!state.isAuthenticated || !el.notificationsBtn) return;
  if (typeof window.EventSource === "function") {
    const source = new EventSource("/api/notifications/stream");
    source.addEventListener("notifications", () => {
      void loadNotifications({ silent: true });
    });
    state.notifications.eventSource = source;
  }
  state.notifications.refreshTimer = setInterval(() => {
    void loadNotifications({ silent: true });
  }, typeof window.EventSource === "function" ? 30000 : 10000);
}

function beginRequest() {
  state.pendingRequests += 1;
  setGlobalLoading(state.pendingRequests > 0);
}

function endRequest() {
  state.pendingRequests = Math.max(0, state.pendingRequests - 1);
  setGlobalLoading(state.pendingRequests > 0);
}

function setButtonBusy(button, busy, caption = "") {
  if (!button) return;
  if (busy) {
    if (!button.dataset.originalText) {
      button.dataset.originalText = button.textContent || "";
    }
    button.disabled = true;
    if (caption) button.textContent = caption;
    return;
  }
  button.disabled = false;
  if (button.dataset.originalText) {
    button.textContent = button.dataset.originalText;
  }
}

function errorText(error, fallback = "Неизвестная ошибка.") {
  if (error && typeof error === "object" && typeof error.message === "string" && error.message.trim()) {
    return error.message.trim();
  }
  if (typeof error === "string" && error.trim()) {
    return error.trim();
  }
  return fallback;
}

function withTimeout(promise, timeoutMs = 2000) {
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      setTimeout(() => reject(new Error("timeout")), timeoutMs);
    }),
  ]);
}

function navigateTopOrSelf(target) {
  try {
    if (window.top && window.top !== window) {
      window.top.location.href = target;
      return;
    }
  } catch (_error) {
    // Cross-origin/top-navigation restrictions: fallback below.
  }
  window.location.assign(target);
}

function currentRelativePath() {
  const path = window.location.pathname || "/";
  const search = window.location.search || "";
  const hash = window.location.hash || "";
  return `${path}${search}${hash}`;
}

function buildAuthUrl(tab = "login") {
  const params = new URLSearchParams();
  const normalizedTab = String(tab || "").trim().toLowerCase();
  if (normalizedTab === "register") {
    params.set("tab", "register");
  }
  params.set("next", currentRelativePath());
  const query = params.toString();
  return query ? `/auth?${query}` : "/auth";
}

function redirectToAuth(tab = "login") {
  if (window.location.pathname === "/auth") return;
  window.location.assign(buildAuthUrl(tab));
}

function openExternalUrl(url, { preferBridge = true } = {}) {
  void preferBridge;
  const target = String(url || "").trim();
  if (!target) throw new Error("Пустая ссылка оплаты.");
  navigateTopOrSelf(target);
  return Promise.resolve();
}

async function resolveUserContext() {
  let me = null;
  try {
    me = await api("/api/auth/me", { skipAuthRedirect: true });
  } catch (error) {
    if (!error || Number(error.status) !== 401) {
      throw error;
    }
  }
  const user = me?.user || {};
  const userId = Number(user?.id || 0);
  if (userId <= 0) {
    return {
      isAuthenticated: false,
      userId: 0,
      username: "",
      email: "",
      emailVerified: false,
      firstName: "",
      lastName: "",
      phone: "",
      oauthProvider: "",
      referralProgram: {},
      isAdmin: false,
      csrfToken: String(me?.csrf_token || ""),
      requiresPdConsent: false,
    };
  }
  const username = String(user?.username || "");
  // Header X-CSRF-Token (applied inside api) wins after session rotation.
  const csrfToken = String(state.csrfToken || me?.csrf_token || "");
  return {
    isAuthenticated: true,
    userId,
    username,
    email: String(user?.email || ""),
    emailVerified: Boolean(user?.email_verified),
    firstName: String(user?.first_name || username),
    lastName: String(user?.last_name || ""),
    phone: String(user?.phone || ""),
    oauthProvider: String(user?.oauth_provider_label || user?.oauth_provider || ""),
    referralProgram: user?.referral_program && typeof user.referral_program === "object" ? user.referral_program : {},
    isAdmin: Boolean(user?.is_admin),
    csrfToken,
    requiresPdConsent: Boolean(user?.requires_pd_consent),
  };
}

async function api(path, { method = "GET", body, isFormData = false, skipAuthRedirect = false, _csrfRetry = false, timeoutMs = 12000, signal, retry = true, headers: extraHeaders = {} } = {}) {
  void skipAuthRedirect;
  const headers = extraHeaders && typeof extraHeaders === "object" ? { ...extraHeaders } : {};
  const normalizedMethod = String(method || "GET").toUpperCase();
  if (body !== undefined && !isFormData) {
    headers["Content-Type"] = "application/json";
  }
  if (!["GET", "HEAD", "OPTIONS"].includes(normalizedMethod) && state.csrfToken) {
    headers["X-CSRF-Token"] = state.csrfToken;
  }

  const requestBody =
    body === undefined
      ? undefined
      : isFormData
        ? body
        : JSON.stringify(body);

  const shouldRetryMethod = normalizedMethod === "GET" && retry;
  const maxAttempts = shouldRetryMethod ? 2 : 1;
  let lastError = null;

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    const ctrl = new AbortController();
    const abortFromCaller = () => ctrl.abort();
    if (signal?.aborted) throw new DOMException("Request cancelled", "AbortError");
    signal?.addEventListener("abort", abortFromCaller, { once: true });
    const timeoutId = setTimeout(() => ctrl.abort("timeout"), Math.max(1000, Number(timeoutMs) || 12000));
    beginRequest();
    try {
      const response = await fetch(path, {
        method: normalizedMethod,
        headers,
        body: requestBody,
        credentials: "same-origin",
        // Bypass old cached redirects as well as stale authenticated responses.
        cache: "no-store",
        signal: ctrl.signal,
      });

      const headerCsrf = String(response.headers.get("X-CSRF-Token") || "").trim();
      if (headerCsrf) state.csrfToken = headerCsrf;

      if (response.headers.get("content-type")?.includes("application/json")) {
        const payload = await response.json();
        if (!response.ok || !payload.ok) {
          const errorTextValue = String(payload?.error || "");
          if (response.status === 403 && payload?.details?.requires_pd_consent) {
            showPdConsentGateForced?.();
          }
          const maintenance = payload?.details?.maintenance;
          if (response.status === 423 && maintenance?.active && !isAdminPage) {
            state.maintenance = normalizeMaintenanceStatus(maintenance);
            window.location.reload();
          }
          const isCsrf =
            response.status === 403
            && /csrf/i.test(errorTextValue)
            && !["GET", "HEAD", "OPTIONS"].includes(normalizedMethod)
            && !_csrfRetry;
          if (isCsrf) {
            try {
              const me = await api("/api/auth/me", { skipAuthRedirect: true, _csrfRetry: true });
              const refreshed = String(me?.csrf_token || state.csrfToken || "").trim();
              if (refreshed) state.csrfToken = refreshed;
            } catch (_refreshError) {
              // fall through to original error
            }
            return api(path, { method, body, isFormData, skipAuthRedirect, _csrfRetry: true, timeoutMs, signal, retry, headers: extraHeaders });
          }
          const details = payload?.details?.required_tier ? ` (${payload.details.required_tier})` : "";
          const requestId = response.headers.get("X-Request-Id");
          const rid = requestId ? ` [rid:${requestId}]` : "";
          const msg = (payload?.error || `HTTP ${response.status}`) + details + rid;
          const err = new Error(msg);
          err.status = response.status;
          err.details = payload?.details && typeof payload.details === "object" ? payload.details : {};
          if (response.status >= 500 && attempt < maxAttempts) {
            lastError = err;
            await new Promise((resolve) => setTimeout(resolve, 250));
            continue;
          }
          throw err;
        }
        if (payload?.data && typeof payload.data === "object" && payload.data.csrf_token) {
          // Prefer header (already applied); keep body only if header absent.
          if (!headerCsrf) state.csrfToken = String(payload.data.csrf_token);
        }
        return payload.data;
      }

      if (!response.ok) {
        const requestId = response.headers.get("X-Request-Id");
        const rid = requestId ? ` [rid:${requestId}]` : "";
        const msg = `HTTP ${response.status}${rid}`;
        const err = new Error(msg);
        err.status = response.status;
        if (response.status >= 500 && attempt < maxAttempts) {
          lastError = err;
          await new Promise((resolve) => setTimeout(resolve, 250));
          continue;
        }
        throw err;
      }

      return response;
    } catch (error) {
      const isAbort = error && error.name === "AbortError";
      if (!signal?.aborted && (isAbort || !navigator.onLine) && attempt < maxAttempts) {
        lastError = error;
        await new Promise((resolve) => setTimeout(resolve, 250));
        continue;
      }
      lastError = error;
      break;
    } finally {
      clearTimeout(timeoutId);
      signal?.removeEventListener("abort", abortFromCaller);
      endRequest();
    }
  }

  if (lastError && lastError.name === "AbortError") {
    throw new Error("Сервер отвечает слишком долго. Попробуйте ещё раз.");
  }
  throw lastError || new Error("Ошибка запроса.");
}

async function sendPresenceHeartbeat() {
  if (
    !state.isAuthenticated
    || state.presenceInFlight
    || document.hidden
    || navigator.onLine === false
  ) return;

  state.presenceInFlight = true;
  const ctrl = new AbortController();
  const timeoutId = window.setTimeout(() => ctrl.abort("timeout"), 8000);
  try {
    const headers = { Accept: "application/json" };
    if (state.csrfToken) headers["X-CSRF-Token"] = state.csrfToken;
    const heartbeatUrl = state.isAdmin
      ? "/api/presence/heartbeat?include_online=1"
      : "/api/presence/heartbeat";
    const response = await fetch(heartbeatUrl, {
      method: "POST",
      headers,
      credentials: "same-origin",
      cache: "no-store",
      signal: ctrl.signal,
    });
    const headerCsrf = String(response.headers.get("X-CSRF-Token") || "").trim();
    if (headerCsrf) state.csrfToken = headerCsrf;
    if (!response.ok || !response.headers.get("content-type")?.includes("application/json")) return;
    const payload = await response.json();
    if (payload?.ok && payload?.data) renderAdminOnlinePresence(payload.data);
  } catch (_error) {
    // Presence is best-effort and must never interrupt the main interface.
  } finally {
    window.clearTimeout(timeoutId);
    state.presenceInFlight = false;
  }
}

function startPresenceHeartbeat() {
  if (state.presenceTimer) {
    window.clearInterval(state.presenceTimer);
    state.presenceTimer = 0;
  }
  if (!state.isAuthenticated) return;
  state.presenceTimer = window.setInterval(() => {
    void sendPresenceHeartbeat();
  }, PRESENCE_HEARTBEAT_INTERVAL_MS);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) void sendPresenceHeartbeat();
  });
  void sendPresenceHeartbeat();
}

function normalizeMaintenanceStatus(value = {}) {
  const schedule = value?.schedule && typeof value.schedule === "object" ? value.schedule : {};
  const contactEmail = String(value?.contact_email || "").trim() || "obolo4ki@kolbabot.ru";
  return {
    active: Boolean(value?.active),
    mode: String(value?.mode || "off"),
    forced: Boolean(value?.forced),
    scheduled: Boolean(value?.scheduled),
    emergencyBypass: Boolean(value?.emergency_bypass),
    schedule,
    contactEmail,
    pollIntervalSeconds: Math.max(2, Number(value?.poll_interval_seconds || 5)),
    updatedBy: Number(value?.updated_by || 0),
    updatedAt: String(value?.updated_at || ""),
  };
}

function maintenanceWindowText(status) {
  const schedule = status?.schedule || {};
  const start = String(schedule.start_time || "02:50");
  const end = String(schedule.end_time || "04:00");
  return `${start}–${end} МСК`;
}

function renderAdminMaintenanceSettings() {
  const status = normalizeMaintenanceStatus(state.admin.maintenance || state.maintenance || {});
  if (el.adminMaintenanceForced) {
    el.adminMaintenanceForced.checked = Boolean(status.forced);
  }
  if (el.adminMaintenanceStatus) {
    const modeText = status.emergencyBypass
      ? "Активен аварийный обход: сайт открыт независимо от режима техработ. После восстановления верните MINIAPP_MAINTENANCE_EMERGENCY_BYPASS в 0."
      : status.forced
      ? "Принудительный режим включён: пользователи увидят страницу ожидания с рабочей почтой."
      : status.scheduled
        ? "Сейчас активно плановое окно техработ."
        : "Пользовательская часть сайта открыта.";
    el.adminMaintenanceStatus.textContent = `${modeText} Плановое окно: ${maintenanceWindowText(status)}. Админка остаётся доступной.`;
  }
}

async function loadMaintenanceStatus({ reloadOnActive = false } = {}) {
  const ctrl = new AbortController();
  const timeoutId = setTimeout(() => ctrl.abort("timeout"), 5000);
  try {
    const response = await fetch("/api/maintenance/status", {
      method: "GET",
      credentials: "same-origin",
      cache: "no-store",
      headers: { Accept: "application/json" },
      signal: ctrl.signal,
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const payload = await response.json();
    if (!payload?.ok || !payload?.data) throw new Error("Maintenance status unavailable");
    const status = normalizeMaintenanceStatus(payload.data);
    state.maintenance = status;
    if (reloadOnActive && status.active && !isAdminPage) {
      window.location.reload();
    }
    return status;
  } finally {
    clearTimeout(timeoutId);
  }
}

function startMaintenanceWatcher() {
  if (isAdminPage || state.maintenanceTimer) return;

  const poll = async () => {
    try {
      await loadMaintenanceStatus({ reloadOnActive: true });
    } catch (_error) {
      // A failed status probe should not interrupt the user's current page.
    }
  };

  state.maintenanceTimer = window.setInterval(poll, MAINTENANCE_POLL_INTERVAL_MS);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) void poll();
  });
  void poll();
}

function hasCookieNoticeAck(){
  try { return localStorage.getItem(COOKIE_NOTICE_KEY) === "1"; } catch (_e) { return false; }
}

async function trackClick(statType, key, label) {
  if (!hasCookieNoticeAck()) return;
  try {
    await api("/api/analytics/click", {
      method: "POST",
      body: {
        user_id: state.userId,
        stat_type: statType,
        button_key: key,
        button_label: label,
      },
    });
  } catch (_error) {
    // Аналитика не должна ломать UX.
  }
}

function resolveStoreProductById(productId) {
  const id = Number(productId || 0);
  if (!id) return null;
  return (state.store.products || []).find((p) => Number(p.id) === id)
    || (state.admin.storeProducts || []).find((p) => Number(p.id) === id)
    || null;
}

function createStoreLinkButton({ link, label, key, productId }) {
  const product = resolveStoreProductById(productId);
  if (product) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "inline-store-btn";
    btn.textContent = label;
    btn.addEventListener("click", () => {
      trackClick("store", key || `product_${product.id}`, label);
      openStoreProductDialog(product);
    });
    return btn;
  }

  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "inline-store-btn";
  btn.textContent = label;
  btn.addEventListener("click", () => {
    trackClick("store", key || link || label, label);
    showToast("Товар для этого ингредиента ещё не привязан в Колбаботе.", true);
  });
  return btn;
}

function updateHeader() {
  const fullName = `${state.userFirstName} ${state.userLastName}`.trim();
  const displayName = state.username || fullName;
  if (state.isAuthenticated) {
    el.userIdBadge.textContent = displayName || "Пользователь";
    el.userRoleBadge.textContent = state.isAdminAccount ? "Администратор" : "Пользователь";
    el.userTierBadge.textContent = tierTitle(state.access);
  } else {
    el.userIdBadge.textContent = "Гость";
    el.userRoleBadge.textContent = "Гостевой режим";
    el.userTierBadge.textContent = "Демо";
  }
  if (el.logoutBtn) {
    el.logoutBtn.classList.toggle("hidden", !state.isAuthenticated);
  }
  if (el.authBtn) {
    el.authBtn.classList.toggle("hidden", state.isAuthenticated);
  }
  if (el.guestIntroCta) {
    const hasGuestIntro = Boolean(String(el.guestIntroCta.textContent || "").trim());
    el.guestIntroCta.classList.toggle("hidden", state.isAuthenticated || !hasGuestIntro);
  }
  if (el.notificationsBtn) {
    el.notificationsBtn.classList.toggle("hidden", !state.isAuthenticated);
    if (!state.isAuthenticated) {
      closeNotificationsPopover();
      updateNotificationsBadge(0);
    }
  }
  updateCheckoutAuthUi();
}
function fillPaywallLinks() {
  const access = state.access || {};
  if (el.premiumLink) el.premiumLink.href = "#";
  el.groupLink.href = access.subscription_group_url || "#";
}

function isAdminCreateSelection(selectEl) {
  const value = String(selectEl?.value || "").trim();
  if (!value) return true;
  const selected = selectEl?.selectedOptions?.[0];
  return Boolean(selected?.dataset?.adminCreate === "1");
}

function appendAdminCreateOption(selectEl, label) {
  const blank = document.createElement("option");
  blank.value = "";
  blank.textContent = label;
  blank.dataset.adminCreate = "1";
  selectEl.appendChild(blank);
}

function refreshAdminDeleteButtons() {
  const pairs = [
    [el.adminFaqSelect, el.adminFaqDeleteBtn],
    [el.adminEquipmentSelect, el.adminEquipmentDeleteBtn],
    [el.adminSausageSelect, el.adminSausageDeleteBtn],
    [el.adminHandbookArticleSelect, el.adminHandbookDeleteBtn],
    [el.adminArticleSelect, el.adminArticleDeleteBtn],
    [el.adminCategorySelect, el.adminCategoryDeleteBtn],
  ];
  pairs.forEach(([selectEl, buttonEl]) => {
    if (!buttonEl) return;
    buttonEl.disabled = isAdminCreateSelection(selectEl);
  });
}

function openPdfBlob(blob, filename = "document.pdf") {
  const url = URL.createObjectURL(blob);
  const opened = window.open(url, "_blank", "noopener,noreferrer");
  if (!opened) {
    const link = document.createElement("a");
    link.href = url;
    link.target = "_blank";
    link.rel = "noopener";
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
  }
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

async function printRecipePdf() {
  if (!state.isAuthenticated) {
    openPaywall("auth_required");
    return;
  }
  if (!state.selectedSausage) {
    throw new Error("Сначала выберите сорт.");
  }
  const fd = new FormData(el.recipeCalcForm);
  const weight = Number(fd.get("weight") || 1);
  const response = await fetch("/api/print/recipe-pdf", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(state.csrfToken ? { "X-CSRF-Token": state.csrfToken } : {}),
    },
    credentials: "same-origin",
    body: JSON.stringify({
      sausage_slug: state.selectedSausage,
      weight_kg: weight,
    }),
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    throw new Error(payload?.error || `HTTP ${response.status}`);
  }
  const blob = await response.blob();
  openPdfBlob(blob, "recipe.pdf");
}

async function printCustomPdf() {
  if (!state.isAuthenticated) {
    openPaywall("auth_required");
    return;
  }
  const linesText = state.lastCustomLines || collectCustomLinesFromRows();
  if (!linesText.trim()) {
    throw new Error("Сначала выполните расчёт.");
  }
  const fd = new FormData(el.customCalcForm);
  const weight = Number(fd.get("weight") || state.lastCustomCalcWeight || 1);
  const response = await fetch("/api/print/custom-pdf", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(state.csrfToken ? { "X-CSRF-Token": state.csrfToken } : {}),
    },
    credentials: "same-origin",
    body: JSON.stringify({
      weight_kg: weight,
      lines: linesText,
    }),
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    throw new Error(payload?.error || `HTTP ${response.status}`);
  }
  const blob = await response.blob();
  openPdfBlob(blob, "custom-calc.pdf");
}

function renderPaymentPlans() {
  const plans = (Array.isArray(state.payments?.plans) ? state.payments.plans : []).filter(
    (item) => String(item.tier || "").trim().toLowerCase() === "premium",
  );
  const byTier = new Map(plans.map((item) => [String(item.tier || "").toLowerCase(), item]));
  const targets = [
    {
      tier: "premium",
      titleEl: el.premiumPlanTitle,
      descriptionEl: el.premiumPlanDescription,
      featuresEl: el.premiumPlanFeatures,
      linkEl: el.premiumLink,
    },
  ];

  targets.forEach(({ tier, titleEl, descriptionEl, featuresEl, linkEl }) => {
    const plan = byTier.get(tier);
    if (!plan) return;

    const planTitle = String(plan.title || "").trim() || resolveTierTitle(tier);
    const amountRub = Math.round(Number(plan.amount_rub || 0));
    const duration = Number(plan.duration_days || state.payments?.duration_days || 30);
    const baseAmount = Math.round(Number(plan.base_amount_rub || amountRub));
    const titleEscaped = escapeHtml(planTitle);
    const pv = state.promoPreview;

    if (titleEl) {
      if (pv && String(pv.tier || "").toLowerCase() === tier) {
        const base = Math.round(Number(pv.base_amount_rub ?? baseAmount));
        const first = Math.round(Number(pv.first_month_amount_rub ?? pv.amount_rub ?? amountRub));
        const regular = Math.round(Number(pv.regular_amount_rub ?? amountRub));
        setHtml(
          titleEl,
          planPaywallTitleWithPromoHtml({
            tierTitleEscaped: titleEscaped,
            baseRub: base,
            firstRub: first,
            regularRub: regular,
            durationDays: duration,
          }),
        );
      } else if (baseAmount > amountRub) {
        setHtml(titleEl, planPriceHeadlineHtml(titleEscaped, baseAmount, amountRub, duration));
      } else {
        titleEl.textContent = `${planTitle} • ${amountRub} ₽ / ${duration} дней`;
      }
    }

    if (descriptionEl) {
      const description = String(plan.description || "").trim();
      descriptionEl.textContent = description || `Подписка на ${duration} дней.`;
    }

    if (featuresEl) {
      const features = Array.isArray(plan.features)
        ? plan.features.map((item) => String(item || "").trim()).filter(Boolean)
        : [];
      if (features.length) {
        setHtml(featuresEl, features.map((item) => `<li>${escapeHtml(item)}</li>`).join(""));
        featuresEl.classList.remove("hidden");
      } else {
        setHtml(featuresEl, "");
        featuresEl.classList.add("hidden");
      }
    }

    if (linkEl) {
      linkEl.textContent = `Оформить ${planTitle}`;
    }
  });

  renderAccountSubscriptionPlans();
}

async function refreshAccessAfterPayment() {
  try {
    const access = await api("/api/access/status");
    state.access = access || null;
    updateHeader();
    renderHomeAccess();
    if (state.access?.can_use_app) {
      renderAccessAlert("");
    }
    await loadBillingInfo();
  } catch (_error) {
    // ignore, не критично для UX.
  }
}

function activeBillingTier() {
  const tier = String(state.billing?.tier || "none").trim().toLowerCase();
  const status = String(state.billing?.status || "").trim().toLowerCase();
  const untilText = String(state.billing?.active_until || "").trim();
  const untilDt = parseStorageDateTime(untilText);
  if (tier !== "premium") return "none";
  if (status !== "active") return "none";
  if (untilDt && untilDt.getTime() <= Date.now()) return "none";
  return tier;
}

function humanTierTitle(rawTier) {
  return resolveTierTitle(rawTier);
}

function confirmSubscriptionChange(nextTier) {
  const normalizedNextTier = String(nextTier || "").trim().toLowerCase();
  const existingTier = activeBillingTier();
  if (existingTier === "none") {
    return true;
  }
  return window.confirm(
    `Сейчас у вас активна подписка ${humanTierTitle(existingTier)}. После оплаты тарифа ${humanTierTitle(normalizedNextTier)} текущая подписка будет аннулирована, и сразу начнется новая 30-дневная подписка. Продолжить?`,
  );
}

function renderAccountSubscriptionPlans() {
  if (!el.accountPlans) return;

  const plans = (Array.isArray(state.payments?.plans) ? state.payments.plans : []).filter(
    (plan) => String(plan?.tier || "").trim().toLowerCase() === "premium",
  );
  const currentTier = activeBillingTier();
  const currentUntil = String(state.billing?.active_until || "").trim();
  el.accountUpgradeCard?.classList.toggle("hidden", Boolean(state.isAuthenticated && currentTier !== "none"));

  if (el.accountPlanNotice) {
    if (!state.isAuthenticated) {
      el.accountPlanNotice.textContent =
        "Посмотрите условия тарифов. Чтобы оформить подписку и включить автоплатёж, войдите в аккаунт.";
    } else if (currentTier !== "none") {
      const untilSuffix = currentUntil ? ` Сейчас активна ${humanTierTitle(currentTier)} до ${formatMskDateTimeLabel(currentUntil, currentUntil)}.` : "";
      el.accountPlanNotice.textContent =
        `Повторная покупка аннулирует текущий период и сразу запускает новый 30-дневный период.${untilSuffix}`;
    } else {
      el.accountPlanNotice.textContent =
        "PREMIUM даёт скидку 5% на товары без доставки, доступ к PREMIUM-статьям и плашку рядом с ником в комментариях.";
    }
  }

  if (!plans.length) {
    setHtml(el.accountPlans, '<div class="muted">Тарифы пока не загружены.</div>');
    return;
  }

  const paymentsEnabled = Boolean(state.payments?.enabled);
  const cards = plans.map((plan) => {
    const tier = String(plan?.tier || "").trim().toLowerCase();
    const title = humanTierTitle(tier);
    const description = String(plan?.description || "").trim();
    const durationDays = Number(plan?.duration_days || state.payments?.duration_days || 30);
    const amountRub = Number(plan?.amount_rub || 0);
    const baseAmountRub = Number(plan?.base_amount_rub || amountRub);
    const discountPercent = Number(plan?.discount_percent || 0);
    const features = Array.isArray(plan?.features) ? plan.features : [];
    const isCurrent = currentTier === tier;
    const rankDelta = tierRank(tier) - tierRank(currentTier);

    let actionLabel = `Оформить ${title}`;
    if (!state.isAuthenticated) {
      actionLabel = `Войти и оформить ${title}`;
    }
    if (currentTier !== "none" && isCurrent) {
      actionLabel = `Новая ${title}`;
    } else if (currentTier !== "none" && rankDelta > 0) {
      actionLabel = `Перейти на ${title}`;
    } else if (currentTier !== "none" && rankDelta < 0) {
      actionLabel = `Спуститься на ${title}`;
    }

    const pv = state.promoPreview;
    const usingPromo = pv && String(pv.tier || "").toLowerCase() === tier;
    const titleEsc = escapeHtml(title);
    const saleRub = Math.round(Number(amountRub));
    const baseListRub = Math.round(Number(baseAmountRub));
    let h3Inner;
    if (usingPromo) {
      const pb = Math.round(Number(pv.base_amount_rub ?? baseListRub));
      const pf = Math.round(Number(pv.first_month_amount_rub ?? pv.amount_rub ?? saleRub));
      const pr = Math.round(Number(pv.regular_amount_rub ?? saleRub));
      h3Inner = planPaywallTitleWithPromoHtml({
        tierTitleEscaped: titleEsc,
        baseRub: pb,
        firstRub: pf,
        regularRub: pr,
        durationDays,
      });
    } else if (baseListRub > saleRub) {
      h3Inner = planPriceHeadlineHtml(titleEsc, baseListRub, saleRub, durationDays);
    } else {
      h3Inner = `${titleEsc} • ${saleRub} ₽ / ${durationDays} дней`;
    }

    let metaExtra = "";
    if (usingPromo) {
      const pf = Math.round(Number(pv.first_month_amount_rub ?? pv.amount_rub ?? saleRub));
      const pr = Math.round(Number(pv.regular_amount_rub ?? saleRub));
      if (pf < pr) {
        metaExtra = ` • промокод только на 1-й период; далее ${pr} ₽ / ${durationDays} дн.`;
      }
    } else if (discountPercent > 0 && baseListRub > saleRub) {
      metaExtra = ` • скидка тарифа ${discountPercent}% на каждый период`;
    }
    const metaLine = `Доступ на ${durationDays} дней${metaExtra}`;

    const currentBadge = isCurrent ? '<span class="plan-card__current">Текущий уровень</span>' : "";
    const featureHtml = features.length
      ? `<ul class="plan-card__features">${features.map((feature) => `<li>${escapeHtml(String(feature || ""))}</li>`).join("")}</ul>`
      : "";

    return `
      <article class="plan-card ${isCurrent ? "is-current" : ""}">
        <h3>${h3Inner}</h3>
        <p>${escapeHtml(description || "Подписка на 30 дней.")}</p>
        <div class="plan-card__meta">${escapeHtml(metaLine)}</div>
        ${featureHtml}
        <div class="plan-card__actions">
          <button type="button" data-account-tier="${escapeHtml(tier)}" ${paymentsEnabled ? "" : "disabled"}>${escapeHtml(actionLabel)}</button>
          ${currentBadge}
        </div>
      </article>
    `;
  });

  setHtml(el.accountPlans, cards.join(""));

  el.accountPlans.querySelectorAll("button[data-account-tier]").forEach((button) => {
    button.addEventListener("click", async () => {
      const nextTier = String(button.dataset.accountTier || "").trim().toLowerCase();
      setResult(el.billingResult, "");
      await startTbankPayment(nextTier, button);
    });
  });
}

async function refreshPaymentPlansData() {
  try {
    const payload = await api("/api/payments/tbank/plans");
    const plans = Array.isArray(payload?.plans) ? payload.plans : [];
    state.payments = {
      ...(state.payments || {}),
      ...(payload || {}),
      plans,
    };
  } catch (_error) {
    // Не блокируем UI: перезагрузка страницы всё равно подтянет актуальные планы.
  }
}

async function waitPaymentConfirmation(orderId, expectedTier = "") {
  const safeOrderId = String(orderId || "").trim();
  if (!safeOrderId) return false;

  const failStatuses = new Set(["CANCELED", "REJECTED", "AUTH_FAIL", "DEADLINE_EXPIRED", "EXPIRED"]);
  const refundStatuses = new Set(["REFUNDED", "REVERSED", "PARTIALLY_REFUNDED"]);
  const successStatuses = new Set(["AUTHORIZED", "CONFIRMED"]);
  const normalizedExpectedTier = String(expectedTier || "").trim().toLowerCase();
  const expectedRank = tierRank(normalizedExpectedTier);

  // При отмене/возврате с формы оплаты браузером (кнопка Back)
  // статус часто не становится финальным сразу. Делать GetState к T-Bank
  // на каждом цикле дорого и может "затыкать" сервер.
  // Поэтому: GetState (refresh=1) только на первой попытке, дальше refresh=0.
  for (let attempt = 1; attempt <= 8; attempt += 1) {
    let statusPayload = null;
    try {
      statusPayload = await api(
        `/api/payments/tbank/status?order_id=${encodeURIComponent(safeOrderId)}&refresh=${attempt === 1 ? 1 : 0}`,
      );
    } catch (_error) {
      // Если сеть/сервер дернулся, пробуем дальше.
    }

    const paymentStatus = String(statusPayload?.status || "").trim().toUpperCase();
    if (successStatuses.has(paymentStatus)) {
      await refreshAccessAfterPayment();
      const currentTier = String(
        state.access?.access_tier || state.access?.paid_tier || statusPayload?.access?.tier || "none",
      ).toLowerCase();
      if (!normalizedExpectedTier || tierRank(currentTier) >= expectedRank) {
        clearPendingPayment();
        showToast(`Оплата ${normalizedExpectedTier ? humanTierTitle(normalizedExpectedTier) : ""} подтверждена.`);
        return true;
      }
    }

    if (failStatuses.has(paymentStatus)) {
      clearPendingPayment();
      showToast("Оплата отклонена или отменена банком.", true);
      return false;
    }

    if (refundStatuses.has(paymentStatus)) {
      clearPendingPayment();
      showToast("Платеж помечен как возврат/отмена.", true);
      return false;
    }

    await new Promise((resolve) => setTimeout(resolve, 1600));
  }

  clearPendingPayment();
  showToast("Платеж создан. Ожидаем подтверждение банка, обновите доступ через 10-20 сек.");
  return false;
}

function legalConsentPrefix() {
  const accountPanel = document.getElementById("tab-account");
  if (accountPanel?.classList.contains("is-active")) {
    return "acc";
  }
  const articlesPanel = document.getElementById("tab-articles");
  if (articlesPanel?.classList.contains("is-active") && articlesPanel.querySelector(".article-premium-gate")) {
    return "art";
  }
  return "pw";
}

function readLegalConsentPayload() {
  const prefix = legalConsentPrefix();
  const gid = (suffix) => document.getElementById(`${prefix}${suffix}`);
  return {
    accept_public_offer: Boolean(gid("AcceptPublicOffer")?.checked),
    accept_subscription_agreement: Boolean(gid("AcceptSubscriptionAgreement")?.checked),
    accept_payment_credentials: Boolean(gid("AcceptPaymentCredentials")?.checked),
    accept_privacy_policy: Boolean(gid("AcceptPrivacyPolicy")?.checked),
  };
}

function validatePaymentConsentsOrToast() {
  const p = readLegalConsentPayload();
  if (!p.accept_public_offer || !p.accept_subscription_agreement || !p.accept_payment_credentials || !p.accept_privacy_policy) {
    showToast("Отметьте все согласия перед оплатой подписки (рекуррентные платежи).", true);
    return false;
  }
  return true;
}

function applyHashNavigation() {
  const raw = (window.location.hash || "").replace(/^#/, "").trim().toLowerCase();
  if (!raw) return;
  const gate = canOpenTab(raw);
  if (gate.ok) {
    activateTab(raw);
  }
}

function bindHomeSubscriptionLink() {
  const link = document.getElementById("homeSubscriptionCancelLink");
  if (!link) return;
  link.addEventListener("click", (event) => {
    event.preventDefault();
    const gate = canOpenTab("account");
    if (!gate.ok) {
      openPaywall(gate.reason);
      return;
    }
    const base = `${window.location.pathname}${window.location.search}`;
    window.history.replaceState(null, "", `${base}#account`);
    activateTab("account");
  });
}

async function startTbankPayment(tier, button) {
  if (button?.dataset?.busy === "1") {
    return;
  }
  const normalizedTier = String(tier || "").trim().toLowerCase();
  if (!state.isAuthenticated) {
    openPaywall("auth_required");
    return;
  }
  if (!state.access?.can_use_app) {
    openPaywall("no_app");
    return;
  }
  if (!state.payments?.enabled) {
    showToast("Оплата временно недоступна. Попробуйте позже.", true);
    return;
  }
  if (!validatePaymentConsentsOrToast()) {
    return;
  }
  if (!confirmSubscriptionChange(normalizedTier)) {
    return;
  }

  setButtonBusy(button, true, "Запускаем подписку...");
  if (button) {
    button.dataset.busy = "1";
  }
  void trackClick("ad", `pay_${normalizedTier}`, `buy_${normalizedTier}`);
  const promoCode = String(el.paymentPromoCode?.value || "").trim().toUpperCase();
  const consent = readLegalConsentPayload();

  try {
    // Первый платеж подписки проводим через Init/PaymentURL:
    // пользователь видит сумму и проходит форму оплаты банка.
    const payment = await api("/api/payments/tbank/init", {
      method: "POST",
      body: {
        user_id: state.userId,
        tier: String(tier || "").trim().toLowerCase(),
        promo_code: promoCode,
        ...consent,
      },
    });

    const orderId = String(payment?.order_id || "").trim();
    const paymentUrl = String(payment?.payment_url || "").trim();
    if (!orderId) {
      throw new Error("Банк не вернул ссылку оплаты.");
    }
    rememberPendingPayment(orderId, tier, state.userId);
    if (payment?.pending_reconciliation) {
      // Init may have reached the bank even though its HTTP response was
      // truncated.  The server has retained this OrderId deliberately, so do
      // not prompt the customer to retry and create a second invoice.
      void waitPaymentConfirmation(orderId, tier);
      clearPendingCardBind();
      if (button?.dataset?.busy === "1") {
        delete button.dataset.busy;
      }
      setButtonBusy(button, false);
      showToast("Запрос на оплату уже отправлен в банк. Операция проверяется; повторно платить не нужно.", true);
      return;
    }
    if (!paymentUrl) {
      throw new Error("Банк не вернул ссылку оплаты.");
    }
    setButtonBusy(button, true, "Открываем форму оплаты...");
    await openExternalUrl(paymentUrl, { preferBridge: true });

    // Если пользователь не вернётся с query-параметрами, всё равно попробуем фоновой опрос.
    void waitPaymentConfirmation(orderId, tier);
    clearPendingCardBind();

    // Если внешняя страница не открылась, не держим кнопку в вечной блокировке.
    setTimeout(() => {
      if (button?.dataset?.busy === "1") {
        delete button.dataset.busy;
        setButtonBusy(button, false);
      }
    }, 4000);
  } catch (error) {
    if (button?.dataset?.busy === "1") {
      delete button.dataset.busy;
    }
    setButtonBusy(button, false);
    showToast(errorText(error, "Не удалось начать оплату. Попробуйте еще раз."), true);
  }
}

function renderAccessAlert(message = "", ctaLink = "", ctaLabel = "Открыть") {
  if (!message) {
    el.accessAlert.classList.add("hidden");
    setHtml(el.accessAlert, "");
    return;
  }

  let action = "";
  if (ctaLink) {
    const safeLink = String(ctaLink || "").trim();
    const isInternal = safeLink.startsWith("/") && !safeLink.startsWith("//");
    const attrs = isInternal ? "" : ' target="_blank" rel="noopener"';
    action = `<a class="button-link"${attrs} href="${escapeHtml(safeLink)}">${escapeHtml(ctaLabel)}</a>`;
  }

  el.accessAlert.classList.remove("hidden");
  setHtml(el.accessAlert, `
    <h2>Ограничение доступа</h2>
    <p>${escapeHtml(message)}</p>
    ${action}
  `);
}

function openPaywall(reason) {
  fillPaywallLinks();
  const map = {
    auth_required: {
      msg: "Изучайте разделы без входа. Для оплаты, сохранения и личного кабинета войдите в аккаунт.",
      link: buildAuthUrl("register"),
      label: "Войти / зарегистрироваться",
    },
    no_app: {
      msg: "Для работы с сайтом нужен активный доступ.",
      link: state.access?.subscription_group_url || "",
      label: "Подробнее",
    },
    recipes: {
      msg: "Войдите в аккаунт, чтобы сохранять свои цены и печатать PDF.",
      link: buildAuthUrl("register"),
      label: "Войти / зарегистрироваться",
    },
    custom: {
      msg: "Войдите в аккаунт, чтобы сохранять свои цены в расчёте.",
      link: buildAuthUrl("register"),
      label: "Войти / зарегистрироваться",
    },
    support: {
      msg: "Личные обращения к технологу доступны после входа в аккаунт.",
      link: buildAuthUrl("register"),
      label: "Войти / зарегистрироваться",
    },
  };

  const info = map[reason] || map.no_app;
  el.paywallMessage.textContent = info.msg;
  renderAccessAlert(info.msg, info.link, info.label);
  setTabVisibility("paywall", true);
  activateTab("paywall");
}

function canOpenTab(tabName) {
  const access = state.access;
  if (!access) return { ok: true };

  if (!state.isAuthenticated) {
    if (tabName === "admin") {
      return { ok: false, reason: "auth_required" };
    }
    return { ok: true };
  }

  const adminPageAccess = tabName === "admin" && state.isAdmin;
  if (!access.can_use_app && !adminPageAccess && !["home", "paywall", "account", "articles", "support"].includes(tabName)) {
    return { ok: false, reason: "no_app" };
  }
  if (tabName === "admin" && !state.isAdmin) {
    return { ok: false, reason: "no_app" };
  }

  return { ok: true };
}

function activateTab(tabName) {
  state.currentTab = tabName;
  el.tabs.forEach((tab) => {
    const isActive = tab.dataset.tab === tabName;
    tab.classList.toggle("is-active", isActive);
    if (isActive) {
      tab.setAttribute("aria-current", "page");
    } else {
      tab.removeAttribute("aria-current");
    }
  });
  el.panels.forEach((panel) => {
    panel.classList.toggle("is-active", panel.id === `tab-${tabName}`);
  });
  const mobilePrimaryTabs = new Set(["home", "recipes", "custom", "account"]);
  el.mobileNavMoreBtn?.classList.toggle("is-active", !mobilePrimaryTabs.has(tabName));
  document.body.classList.toggle("admin-ui-open", tabName === "admin");
  if (tabName === "account") {
    if (orderIdFromUrl()) {
      const ordersCard = document.querySelector(".account-orders-card");
      if (ordersCard) ordersCard.open = true;
    }
    void loadAccountOrders();
    void loadAccountOauthConnections({ initializeProviders: Boolean(el.accountOauthCard?.open) });
  }
  if (tabName === "articles") void markCurrentArticleRead();
}

function setTabVisibility(tabName, visible) {
  el.tabs.forEach((tab) => {
    if (tab.dataset.tab === tabName) {
      tab.classList.toggle("hidden", !visible);
    }
  });
}

function setMobileNavOpen(open, { restoreFocus = true } = {}) {
  if (!el.mobileNavSheet || !el.mobileNavMoreBtn) return;
  const isOpen = Boolean(open);
  el.mobileNavSheet.classList.toggle("is-open", isOpen);
  el.mobileNavSheet.setAttribute("aria-hidden", isOpen ? "false" : "true");
  el.mobileNavMoreBtn.setAttribute("aria-expanded", isOpen ? "true" : "false");
  document.body.classList.toggle("mobile-nav-open", isOpen);

  if (isOpen) {
    window.requestAnimationFrame(() => el.mobileNavSheet.querySelector(".mobile-nav-sheet__close")?.focus());
  } else if (restoreFocus && document.activeElement && el.mobileNavSheet.contains(document.activeElement)) {
    el.mobileNavMoreBtn.focus();
  }
}

function setupMobileNavigation() {
  if (!el.mobileNavSheet || !el.mobileNavMoreBtn) return;
  el.mobileNavMoreBtn.addEventListener("click", () => {
    setMobileNavOpen(!el.mobileNavSheet.classList.contains("is-open"), { restoreFocus: false });
  });
  el.mobileNavSheet.querySelectorAll("[data-mobile-nav-close]").forEach((button) => {
    button.addEventListener("click", () => setMobileNavOpen(false));
  });
  document.addEventListener("keydown", (event) => {
    if (!el.mobileNavSheet.classList.contains("is-open")) return;
    if (event.key === "Escape") {
      setMobileNavOpen(false);
      return;
    }
    if (event.key === "Tab") {
      const focusable = Array.from(el.mobileNavSheet.querySelectorAll("button:not(:disabled)"))
        .filter((button) => button.tabIndex >= 0 && !button.classList.contains("hidden") && button.getClientRects().length > 0);
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!first || !last) return;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  });
}

function setupTabs() {
  el.tabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      const tabName = tab.dataset.tab;
      const gate = canOpenTab(tabName);
      if (!gate.ok) {
        openPaywall(gate.reason);
        return;
      }
      activateTab(tabName);
      if (tab.closest(".mobile-bottom-nav, .mobile-nav-sheet")) {
        setMobileNavOpen(false, { restoreFocus: false });
        const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        document.querySelector("main")?.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "start" });
      }
    });
  });

  document.querySelectorAll("[data-jump]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const target = btn.dataset.jump;
      const gate = canOpenTab(target);
      if (!gate.ok) {
        openPaywall(gate.reason);
        return;
      }
      activateTab(target);
    });
  });
  setupMobileNavigation();
}

function renderHomeStats(bootstrap) {
  setHtml(el.homeStats, "");
  [
    `Категорий: ${bootstrap.categories_count}`,
    `Оборудование: ${bootstrap.equipment_count}`,
    `FAQ: ${bootstrap.faq_count}`,
    `Справочник: ${Array.isArray(bootstrap.handbook_sections) ? bootstrap.handbook_sections.length : 0}`,
  ].forEach((text) => {
    const span = document.createElement("span");
    span.className = "stat-pill";
    span.textContent = text;
    el.homeStats.appendChild(span);
  });
}

function renderHomeAccess() {
  const access = state.access;
  if (!access) {
    el.homeAccessInfo.textContent = "Данные доступа не получены.";
    return;
  }

  if (!state.isAuthenticated) {
    const guestBlocks = [
      "Режим: гостевой просмотр",
      "Рецепты, расчёты, справочник, FAQ и оборудование — без подписки",
      "Вход нужен для сохранения цен и личного кабинета",
      "Поддержка — после входа в аккаунт",
    ];
    setHtml(el.homeAccessInfo, guestBlocks.map((x) => `<div>${escapeHtml(x)}</div>`).join(""));
    return;
  }

  const blocks = [
    `Базовый уровень: ${access.is_member ? "есть" : "нет"}`,
    `Тариф: ${tierTitle(access)}`,
    `Оплачен до: ${access.paid_until ? access.paid_until : "—"}`,
    "Рецепты: доступно",
    "Свой расчёт: доступно",
    "Поддержка: доступно",
  ];
  setHtml(el.homeAccessInfo, blocks.map((x) => `<div>${escapeHtml(x)}</div>`).join(""));
}

async function handlePaymentReturnParams() {
  const params = new URLSearchParams(window.location.search);
  const storePaymentResult = String(params.get("store_payment_result") || "").trim().toLowerCase();
  const storeOrderId = String(params.get("store_order_id") || "").trim();
  if (storePaymentResult) {
    if (storePaymentResult === "success") {
      clearStoreCheckoutAttempt();
      clearStoreCart();
      showToast(storeOrderId ? `Заказ ${storeOrderId} оформлен.` : "Заказ оформлен.");
      if (state.isAdmin) {
        void loadAdminOrders(state.admin.ordersStatus || "current");
      }
    } else if (storePaymentResult === "fail") {
      clearStoreCheckoutAttempt();
      showToast("Оплата заказа не завершена. Корзина сохранена для повторной попытки.", true);
    }
    params.delete("store_payment_result");
    params.delete("store_order_id");
    const cleanQuery = params.toString();
    const cleanUrl = cleanQuery ? `${window.location.pathname}?${cleanQuery}` : window.location.pathname;
    window.history.replaceState({}, "", cleanUrl);
    return true;
  }

  const paymentResult = String(params.get("payment_result") || "").trim().toLowerCase();
  if (!paymentResult) return false;

  const pending = readPendingPayment();
  const tier = String(params.get("tier") || pending?.tier || "").trim().toLowerCase();
  const orderId = String(params.get("order_id") || pending?.order_id || "").trim();
  if (paymentResult === "success") {
    const title = tier ? humanTierTitle(tier) : "";
    showToast(`Проверяем оплату ${title}...`);
    if (orderId) {
      const pendingUserId = Number(pending?.user_id || 0) || 0;
      if (pendingUserId > 0 && state.userId > 0 && pendingUserId !== state.userId) {
        // Платеж принадлежит другому аккаунту — не продолжаем проверку статуса.
        clearPendingPayment();
      } else {
        void waitPaymentConfirmation(orderId, tier);
      }
    } else {
      void refreshAccessAfterPayment();
    }
  } else if (paymentResult === "fail") {
    clearPendingPayment();
    showToast("Оплата не завершена. Попробуйте еще раз.", true);
  }

  params.delete("payment_result");
  params.delete("tier");
  params.delete("order_id");
  const cleanQuery = params.toString();
  const cleanUrl = cleanQuery ? `${window.location.pathname}?${cleanQuery}` : window.location.pathname;
  window.history.replaceState({}, "", cleanUrl);
  return true;
}

async function resumePendingPaymentCheck() {
  const pending = readPendingPayment();
  if (!pending?.order_id) return;
  if (!state.userId || state.userId <= 0) {
    clearPendingPayment();
    return;
  }

  const pendingUserId = Number(pending.user_id || 0) || 0;
  if (!pendingUserId) {
    // Старые сохранения без user_id потенциально могут тормозить UI при смене аккаунта.
    // Лучше не продолжать ожидание.
    return;
  }
  if (pendingUserId !== state.userId) {
    clearPendingPayment();
    return;
  }

  showToast(`Проверяем статус платежа ${pending.tier ? humanTierTitle(pending.tier) : ""}...`);
  await waitPaymentConfirmation(pending.order_id, pending.tier || "");
}

async function resumePendingCardBindFlow() {
  const pending = readPendingCardBind();
  if (!pending?.request_key) return;
  if (!state.userId || state.userId <= 0) {
    clearPendingCardBind();
    return;
  }
  if (pending.user_id > 0 && pending.user_id !== state.userId) {
    clearPendingCardBind();
    return;
  }
  const tier = String(pending.tier || "").trim().toLowerCase();
  if (tier !== "premium") {
    clearPendingCardBind();
    return;
  }

  setResult(el.billingResult, "Возобновляем привязку карты после возврата из банка...");
  let lastStatusLine = "";
  let lastErrorCode = "";
  let hasRebillId = false;
  for (let i = 0; i < 25; i += 1) {
    await new Promise((r) => setTimeout(r, 2000));
    let st;
    try {
      st = await api(`/api/payments/tbank/add-card/state?request_key=${encodeURIComponent(pending.request_key)}`);
    } catch (error) {
      const message = errorText(error, "");
      if (message.includes("request_key") || message.includes("Неизвестный") || message.includes("чужой")) {
        clearPendingCardBind();
        setResult(el.billingResult, "Сессия привязки карты устарела. Запустите привязку заново.", true);
        return;
      }
      throw error;
    }
    const hasRebill = Boolean(st?.has_rebill);
    const status = String(st?.status || "").toUpperCase();
    const errorCode = String(st?.error_code || "").trim();
    const message = String(st?.message || "").trim();
    const statusLine = status
      ? `Статус привязки карты: ${status}${errorCode ? ` (код ${errorCode})` : ""}${message ? ` — ${message}` : ""}`
      : "";
    if (status && (statusLine !== lastStatusLine || errorCode !== lastErrorCode)) {
      // Не спамим одинаковыми тостами на каждом цикле опроса.
      setResult(el.billingResult, statusLine, false);
      lastStatusLine = statusLine;
      lastErrorCode = errorCode;
    }
    if (hasRebill) {
      hasRebillId = true;
      break;
    }
    if (!hasRebill && errorCode && errorCode !== "0") {
      clearPendingCardBind();
      setResult(el.billingResult, statusLine || "Привязка карты отклонена.", true);
      return;
    }
  }

  if (!hasRebillId) {
    clearPendingCardBind();
    setResult(el.billingResult, "Не удалось подтвердить привязку карты. Запустите привязку заново.", true);
    return;
  }

  const consent = pending.consent || readLegalConsentPayload();
  const sub = await api("/api/payments/tbank/subscribe", {
    method: "POST",
    body: {
      user_id: state.userId,
      tier,
      promo_code: pending.promo_code || "",
      ...consent,
    },
  });
  if (sub?.need_bind) {
    clearPendingCardBind();
    setResult(el.billingResult, "Карта пока не привязана. Завершите привязку на форме банка и повторите попытку.", true);
    return;
  }
  clearPendingCardBind();
  const orderId = String(sub?.order_id || "").trim();
  if (orderId) {
    rememberPendingPayment(orderId, tier, state.userId);
    await waitPaymentConfirmation(orderId, tier);
  } else {
    await loadBillingInfo();
  }
}

function tryInitCookieNotice() {
  if (!el.cookieNoticeBar || el.cookieNoticeBar.dataset.bound === "1") return;
  let stored = "";
  try {
    stored = window.localStorage.getItem(COOKIE_NOTICE_KEY) || "";
  } catch (_e) {
    return;
  }
  if (stored) return;
  el.cookieNoticeBar.classList.remove("hidden");
  el.cookieNoticeBar.dataset.bound = "1";
  el.cookieNoticeAccept?.addEventListener("click", async () => {
    try {
      window.localStorage.setItem(COOKIE_NOTICE_KEY, new Date().toISOString());
    } catch (_e) {
      /* ignore */
    }
    el.cookieNoticeBar?.classList.add("hidden");
    if (state.userId > 0) {
      try {
        await api("/api/auth/legal/cookie-notice", { method: "POST", body: { user_id: state.userId } });
      } catch (_e) {
        /* optional */
      }
    }
  });
}

function bindPdConsentGateOnce() {
  if (!el.pdConsentGate || el.pdConsentGate.dataset.bound === "1") return;
  el.pdConsentGate.dataset.bound = "1";
  el.pdConsentCheckbox?.addEventListener("change", () => {
    if (el.pdConsentSubmit) {
      el.pdConsentSubmit.disabled = !el.pdConsentCheckbox?.checked;
    }
  });
  el.pdConsentSubmit?.addEventListener("click", async () => {
    if (!el.pdConsentCheckbox?.checked) return;
    setButtonBusy(el.pdConsentSubmit, true, "Сохраняем...");
    try {
      await api("/api/auth/legal/pd-consent", {
        method: "POST",
        body: { user_id: state.userId, pd_consent: true },
      });
      el.pdConsentGate?.classList.add("hidden");
      document.body.classList.remove("pd-gate-open");
      await loadBootstrap();
    } catch (error) {
      showToast(error.message || "Не удалось сохранить согласие.", true);
    } finally {
      setButtonBusy(el.pdConsentSubmit, false);
    }
  });
}

function showPdConsentGateIfNeeded(bootstrapUser) {
  bindPdConsentGateOnce();
  if (!bootstrapUser?.requires_pd_consent) return;
  el.pdConsentGate?.classList.remove("hidden");
  document.body.classList.add("pd-gate-open");
  if (el.pdConsentCheckbox) el.pdConsentCheckbox.checked = false;
  if (el.pdConsentSubmit) el.pdConsentSubmit.disabled = true;
}

function showPdConsentGateForced() {
  bindPdConsentGateOnce();
  el.pdConsentGate?.classList.remove("hidden");
  document.body.classList.add("pd-gate-open");
  if (el.pdConsentCheckbox) el.pdConsentCheckbox.checked = false;
  if (el.pdConsentSubmit) el.pdConsentSubmit.disabled = true;
}

function safePublicSocialUrl(raw) {
  const value = String(raw || "").trim();
  if (!value) return "";
  try {
    const parsed = new URL(value);
    if (!["http:", "https:"].includes(parsed.protocol) || !parsed.hostname || parsed.username || parsed.password) {
      return "";
    }
    return parsed.href;
  } catch (_error) {
    return "";
  }
}

function safeActionUrl(raw) {
  const value = String(raw || "").trim();
  if (!value || value.includes("\\") || /[\u0000-\u001f]/.test(value)) return "";
  if (value.startsWith("/") && !value.startsWith("//")) return value;
  return safePublicSocialUrl(value);
}

function normalizeSiteSocialLinks(rawLinks) {
  const source = rawLinks && typeof rawLinks === "object" ? rawLinks : {};
  return Object.fromEntries(
    SITE_SOCIAL_LINK_KEYS.map((key) => [key, safePublicSocialUrl(source[key])]),
  );
}

function renderHeroSocialLinks(rawLinks) {
  if (!el.heroSocialLinks) return;
  const links = normalizeSiteSocialLinks(rawLinks);
  let visibleCount = 0;
  el.heroSocialLinks.querySelectorAll("[data-social-link]").forEach((anchor) => {
    const key = String(anchor.dataset.socialLink || "");
    const href = links[key] || "";
    anchor.classList.toggle("hidden", !href);
    if (href) {
      anchor.href = href;
      visibleCount += 1;
    } else {
      anchor.removeAttribute("href");
    }
  });
  el.heroSocialLinks.classList.toggle("hidden", visibleCount === 0);
}

async function loadBootstrap() {
  const bootstrap = await api("/api/bootstrap");
  const backendAdmin = Boolean(bootstrap.is_admin);
  state.isAdminAccount = backendAdmin;
  state.isAdmin = Boolean(backendAdmin && isAdminPage);
  state.access = bootstrap.access || null;
  state.payments = bootstrap.payments || null;
  state.siteSocialLinks = normalizeSiteSocialLinks(bootstrap.site_social_links);
  state.csrfToken = String(state.csrfToken || bootstrap.csrf_token || "");

  updateHeader();
  renderHeroSocialLinks(state.siteSocialLinks);
  renderHomeStats(bootstrap);
  renderHomeAccess();
  fillPaywallLinks();
  renderPaymentPlans();

  if (state.isAuthenticated) {
    const hello = state.username ? `, ${state.username}` : (state.userFirstName ? `, ${state.userFirstName}` : "");
    el.welcomeText.textContent = `Привет${hello}. Сайт готов к работе.`;
  } else {
    el.welcomeText.textContent = "Изучайте разделы без входа. Для оплаты, сохранения и кабинета войдите в аккаунт.";
  }

  if (isAdminPage) {
    el.tabs.forEach((tabBtn) => {
      const isAdminTab = tabBtn.dataset.tab === "admin";
      tabBtn.classList.toggle("hidden", !isAdminTab);
    });
    if (!backendAdmin) {
      renderAccessAlert("Недостаточно прав. Эта страница доступна только администраторам.");
      showToast("Недостаточно прав администратора.", true);
      throw new Error("Нет прав администратора.");
    }
    setTabVisibility("admin", true);
    el.adminSection.classList.remove("hidden");
    activateTab("admin");
  } else {
    setTabVisibility("admin", false);
    el.adminSection.classList.add("hidden");
  }

  if (state.isAuthenticated && !state.access?.can_use_app && !state.isAdmin) {
    openPaywall("no_app");
  } else {
    renderAccessAlert("");
  }

  if (state.isAuthenticated) {
    const handledReturnParams = await handlePaymentReturnParams();
    if (!handledReturnParams) {
      void resumePendingPaymentCheck();
    }
  }
  return bootstrap;
}

function collectCurrentRecipeChoices() {
  const choices = {};
  (state.currentRecipe?.ingredients || []).forEach((item) => {
    const optionKey = String(item?.selected_option_key || "").trim();
    if ((item?.options || []).length > 0 && optionKey) {
      choices[String(item.full_name || "")] = optionKey;
    }
  });
  return choices;
}

function recipeOptionAmountText(option) {
  if (!option?.is_numeric) return String(option?.value ?? "");
  const unitLabel = { g: "г", kg: "кг", m: "м", l: "л", ml: "мл" }[option.amount_unit] || "";
  return `${option.value}${unitLabel ? ` ${unitLabel}` : ""} на 1 кг`;
}

function renderRecipe(recipe) {
  state.currentRecipe = recipe;
  state.lastRecipeCalc = null;
  if (el.recipeCartHint) {
    setHtml(el.recipeCartHint, "");
    el.recipeCartHint.classList.add("hidden");
  }
  el.recipeTitle.textContent = recipe.display_name || "Рецепт";
  const photoUrl = String(recipe.recipe_photo_url || "").trim();
  if (el.recipeImage) {
    if (photoUrl) {
      el.recipeImage.src = photoUrl;
      el.recipeImage.classList.remove("hidden");
    } else {
      el.recipeImage.classList.add("hidden");
      el.recipeImage.removeAttribute("src");
    }
  }
  el.recipeText.textContent = recipe.recipe_text || "Текст рецепта пока не заполнен.";
  clearErrorTextState(el.recipeText);
  setHtml(el.recipeIngredients, "");

  if (!Array.isArray(recipe.ingredients) || recipe.ingredients.length === 0) {
    const empty = document.createElement("div");
    empty.className = "muted";
    empty.textContent = "Ингредиенты не заполнены.";
    el.recipeIngredients.appendChild(empty);
    return;
  }

  recipe.ingredients.forEach((item) => {
    const row = document.createElement("div");
    row.className = "ingredient";

    const head = document.createElement("div");
    head.className = "ingredient-head";

    const options = Array.isArray(item.options) ? item.options : [];
    if (options.length > 0) {
      const strong = document.createElement("strong");
      strong.textContent = item.full_name;
      head.appendChild(strong);
    } else if (item.link || item.product_id) {
      head.appendChild(
        createStoreLinkButton({
          link: item.link,
          label: item.full_name,
          key: `ingredient_${item.short_key || item.full_name}`,
          productId: item.product_id,
        }),
      );
    } else {
      const strong = document.createElement("strong");
      strong.textContent = item.full_name;
      head.appendChild(strong);
    }

    const meta = document.createElement("div");
    meta.className = "ingredient-meta";
    if (options.length > 0) {
      const choiceWrap = document.createElement("div");
      choiceWrap.className = "recipe-ingredient-choice";
      const select = document.createElement("select");
      select.setAttribute("aria-label", `Выбор для ${item.full_name}`);
      options.forEach((option) => {
        const optionEl = document.createElement("option");
        optionEl.value = String(option.option_key || "");
        optionEl.textContent = option.full_name || "Вариант";
        select.appendChild(optionEl);
      });
      const selectedKey = options.some((option) => String(option.option_key || "") === String(item.selected_option_key || ""))
        ? String(item.selected_option_key || "")
        : String(options[0]?.option_key || "");
      item.selected_option_key = selectedKey;
      select.value = selectedKey;
      const selectedOption = options.find((option) => String(option.option_key || "") === selectedKey) || options[0];
      meta.textContent = recipeOptionAmountText(selectedOption);

      select.addEventListener("change", async () => {
        const previousKey = String(item.selected_option_key || "");
        const nextKey = String(select.value || "");
        if (!state.isAuthenticated) {
          select.value = previousKey;
          openPaywall("auth_required");
          return;
        }
        const hadCalculation = Boolean(state.lastRecipeCalc);
        item.selected_option_key = nextKey;
        const nextOption = options.find((option) => String(option.option_key || "") === nextKey);
        meta.textContent = recipeOptionAmountText(nextOption);
        select.disabled = true;
        try {
          await api(`/api/recipes/${encodeURIComponent(recipe.slug)}/choices`, {
            method: "POST",
            body: { choices: collectCurrentRecipeChoices() },
          });
          state.lastRecipeCalc = null;
          if (hadCalculation) {
            try {
              await runRecipeCalculation();
            } catch (calcError) {
              setResult(el.recipeCalcResult, calcError.message || "Выбор сохранён, но пересчёт не выполнен.", true);
            }
          } else {
            setResult(el.recipeCalcResult, "Выбор сохранён. Нажмите «Рассчитать».");
          }
          showToast("Выбор ингредиента сохранён.");
        } catch (error) {
          item.selected_option_key = previousKey;
          select.value = previousKey;
          const previousOption = options.find((option) => String(option.option_key || "") === previousKey) || options[0];
          meta.textContent = recipeOptionAmountText(previousOption);
          showToast(error.message || "Не удалось сохранить выбор.", true);
        } finally {
          select.disabled = false;
        }
      });
      choiceWrap.appendChild(select);
      row.append(head, choiceWrap, meta);
      el.recipeIngredients.appendChild(row);
      return;
    }

    meta.textContent = recipeOptionAmountText(item);

    row.append(head, meta);
    el.recipeIngredients.appendChild(row);
  });
}

async function loadRecipe(slug) {
  if (!slug) return;
  const requestId = ++state.recipeLoadRequestId;
  const isCurrent = () => state.recipeLoadRequestId === requestId && state.selectedSausage === slug;
  try {
    const recipe = await api(`/api/recipes/${encodeURIComponent(slug)}`);
    if (isCurrent()) renderRecipe(recipe);
  } catch (error) {
    if (isCurrent()) {
      state.currentRecipe = null;
      throw error;
    }
  }
}

function renderRecipeCalcResult(data) {
  const wrapper = document.createElement("div");

  const total = document.createElement("div");
  total.className = "result-total";
  total.textContent = `Себестоимость на ${data.weight_kg} кг: ${Number(data.total_cost).toFixed(2)} ₽`;
  wrapper.appendChild(total);

  const list = document.createElement("div");
  list.className = "result-list";

  (data.items || []).forEach((item) => {
    if (item.is_text) {
      const textLine = document.createElement("div");
      textLine.className = "muted";
      textLine.textContent = `${item.full_name}: ${item.raw_value}`;
      list.appendChild(textLine);
      return;
    }

    const row = document.createElement("div");
    row.className = "result-row";

    const c1 = document.createElement("div");
    if (item.link || item.store_product || item.product_id) {
      const linkBtn = createStoreLinkButton({
        link: item.link,
        label: item.full_name,
        key: `ingredient_${item.short_key || item.full_name}`,
        productId: item.store_product?.id || item.product_id,
      });
      linkBtn.classList.add("compact");
      c1.appendChild(linkBtn);
    } else {
      c1.textContent = item.full_name;
    }

    const c2 = document.createElement("div");
    c2.textContent = item.amount_text;

    const c3 = document.createElement("div");
    c3.textContent = `${Number(item.cost).toFixed(2)} ₽`;

    row.append(c1, c2, c3);
    list.appendChild(row);
  });

  wrapper.appendChild(list);

  const cartItems = (data.items || []).filter((item) => item.store_product && Number(item.cart_quantity || 0) > 0);
  if (el.recipeCartHint) {
    setHtml(el.recipeCartHint, "");
    if (cartItems.length > 0) {
      const lines = cartItems.map((item) => `${item.store_product.title || item.full_name} × ${Math.ceil(Number(item.cart_quantity || 1))}`);
      el.recipeCartHint.textContent = `В корзину попадёт: ${lines.join("; ")}.`;
      el.recipeCartHint.classList.remove("hidden");
    } else {
      el.recipeCartHint.classList.add("hidden");
    }
  }
  const editable = [];
  const seen = new Set();
  (data.items || []).forEach((item) => {
    if (item.is_text || !item.short_key || seen.has(item.short_key)) return;
    seen.add(item.short_key);
    editable.push(item);
  });

  if (editable.length > 0 && state.isAuthenticated) {
    const title = document.createElement("h3");
    title.textContent = "Скорректировать цены";
    title.style.marginTop = "14px";
    wrapper.appendChild(title);

    const editor = document.createElement("div");
    editor.className = "result-price-editor";

    editable.forEach((item) => {
      const line = document.createElement("div");
      line.className = "price-editor-item";

      const name = document.createElement("div");
      const unitLabel = item.price_unit === "l" ? "за 1л" : item.price_unit === "m" ? "за 1м" : "за 1кг";
      name.textContent = `${item.full_name} (${unitLabel})`;

      const input = document.createElement("input");
      input.type = "number";
      input.step = "0.01";
      input.min = "0";
      input.autocomplete = "off";
      input.inputMode = "decimal";
      input.value = String(item.unit_price || 0);
      input.dataset.priceKey = String(item.short_key || "");
      input.dataset.itemName = String(item.full_name || "");

      line.append(name, input);
      editor.appendChild(line);
    });

    const actions = document.createElement("div");
    actions.className = "inline-actions";

    const saveAllBtn = document.createElement("button");
    saveAllBtn.type = "button";
    saveAllBtn.textContent = "Сохранить все цены и пересчитать";

    const status = document.createElement("div");
    status.className = "muted";

    saveAllBtn.addEventListener("click", async () => {
      try {
        const prices = Array.from(editor.querySelectorAll("input[data-price-key]")).map((input) => {
          const key = String(input.dataset.priceKey || "").trim();
          const value = Number(input.value);
          if (!key) {
            throw new Error("Не найден ключ цены для одного из ингредиентов.");
          }
          if (!Number.isFinite(value) || value < 0) {
            const itemName = input.dataset.itemName || key;
            throw new Error(`Некорректная цена у «${itemName}».`);
          }
          return { price_key: key, price: value };
        });

        if (!prices.length) {
          throw new Error("Нет цен для сохранения.");
        }

        saveAllBtn.disabled = true;
        setResult(status, "Сохраняем цены...");

        const bulkResult = await api("/api/user-prices/bulk", {
          method: "POST",
          body: {
            user_id: state.userId,
            prices,
          },
        });

        await runRecipeCalculation();
        setResult(status, `Сохранено цен: ${bulkResult.updated_count}.`);
        showToast(`Сохранено цен: ${bulkResult.updated_count}.`);
      } catch (error) {
        setResult(status, error.message, true);
      } finally {
        saveAllBtn.disabled = false;
      }
    });

    actions.append(saveAllBtn, status);
    wrapper.append(editor, actions);
  }

  setHtml(el.recipeCalcResult, "");
  clearErrorTextState(el.recipeCalcResult);
  el.recipeCalcResult.classList.add("muted");
  el.recipeCalcResult.appendChild(wrapper);
  state.lastRecipeCalc = data;
}

async function runRecipeCalculation() {
  if (!state.selectedSausage) {
    throw new Error("Сначала выберите сорт.");
  }

  const fd = new FormData(el.recipeCalcForm);
  const weight = Number(fd.get("weight") || 0);
  if (!Number.isFinite(weight) || weight <= 0) {
    throw new Error("Введите корректный вес партии.");
  }

  const result = await api("/api/calc/recipe", {
    method: "POST",
    body: {
      user_id: state.userId,
      sausage_slug: state.selectedSausage,
      weight_kg: weight,
      choices: collectCurrentRecipeChoices(),
    },
  });

  state.lastRecipeCalc = result;
  renderRecipeCalcResult(result);
}

function addRecipeIngredientsToCart() {
  const items = (state.lastRecipeCalc?.items || []).filter((item) => item?.store_product && Number(item.cart_quantity || 0) > 0);
  if (!items.length) {
    throw new Error("Для рассчитанного рецепта нет привязанных товаров. Рассчитайте рецепт или попросите администратора настроить привязки.");
  }
  items.forEach((item) => {
    const product = item.store_product;
    const qty = Math.max(1, Math.ceil(Number(item.cart_quantity || 1)));
    addToCart(product, { silent: true, quantity: qty });
  });
  showToast(`Ингредиенты добавлены в корзину: ${items.length}.`);
}

function parseCustomLinesForEditor(linesText) {
  const rows = String(linesText || "")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => Boolean(line));

  return rows.map((line, idx) => {
    const parts = line.split("|");
    const name = String(parts[0] || "").trim();
    const quantity = String(parts[1] || "").trim();
    const price = String(parts[2] || "").trim();
    return {
      idx,
      name,
      quantity,
      price,
    };
  });
}

function makeCustomIngredientRow(item = {}) {
  const row = document.createElement("div");
  row.className = "custom-ingredient-row";

  const name = document.createElement("input");
  name.type = "text";
  name.placeholder = "Название";
  name.value = String(item.name || "").trim();
  name.dataset.field = "name";
  name.autocomplete = "off";

  const grams = document.createElement("input");
  grams.type = "number";
  grams.step = "0.01";
  grams.min = "0";
  grams.placeholder = "гр на 1 кг";
  grams.value = String(item.quantity || "").trim();
  grams.dataset.field = "quantity";
  grams.autocomplete = "off";
  grams.inputMode = "decimal";

  const price = document.createElement("input");
  price.type = "number";
  price.step = "0.01";
  price.min = "0";
  price.placeholder = "цена за кг/л/м";
  price.value = String(item.price || "").trim();
  price.dataset.field = "price";
  price.autocomplete = "off";
  price.inputMode = "decimal";

  const remove = document.createElement("button");
  remove.type = "button";
  remove.className = "danger mini";
  remove.textContent = "Удалить";
  remove.addEventListener("click", () => {
    row.remove();
  });

  row.append(name, grams, price, remove);
  return row;
}

function collectCustomLinesFromRows() {
  const rows = Array.from(el.customIngredientsRows?.querySelectorAll(".custom-ingredient-row") || []);
  const lines = [];
  rows.forEach((row) => {
    const name = String(row.querySelector('[data-field="name"]')?.value || "").trim();
    const quantity = String(row.querySelector('[data-field="quantity"]')?.value || "").trim();
    const price = String(row.querySelector('[data-field="price"]')?.value || "").trim();
    if (!name && !quantity && !price) return;
    if (!name || !quantity || !price) {
      throw new Error("Заполните все поля строки: название, гр на 1 кг и цену.");
    }
    lines.push(`${name} | ${quantity} | ${price}`);
  });
  if (!lines.length) {
    throw new Error("Добавьте хотя бы один ингредиент.");
  }
  return lines.join("\n");
}

function initCustomIngredientRows() {
  if (!el.customIngredientsRows) return;
  if (el.customIngredientsRows.children.length > 0) return;
  const defaults = [
    { name: "Свинина", quantity: "700", price: "380" },
    { name: "Говядина", quantity: "300", price: "620" },
  ];
  defaults.forEach((item) => {
    el.customIngredientsRows.appendChild(makeCustomIngredientRow(item));
  });
}

function renderCustomResult(data, sourceLinesText = "") {
  state.lastCustomCalc = data;
  const wrapper = document.createElement("div");

  const total = document.createElement("div");
  total.className = "result-total";
  total.textContent = `Итог: ${Number(data.total_cost).toFixed(2)} ₽`;
  wrapper.appendChild(total);

  const list = document.createElement("div");
  list.className = "result-list";
  (data.items || []).forEach((item) => {
    const row = document.createElement("div");
    row.className = "result-row";
    setHtml(row, `<div>${escapeHtml(item.name)}</div><div>${escapeHtml(item.amount_text)}</div><div>${Number(item.cost).toFixed(2)} ₽</div>`);
    list.appendChild(row);
  });

  wrapper.appendChild(list);
  const parsedLines = parseCustomLinesForEditor(sourceLinesText || state.lastCustomLines);
  const editableItems = (data.items || []).filter((item, idx) => {
    const source = parsedLines[idx];
    return Boolean(item && source && source.name && source.quantity);
  });

  if (editableItems.length > 0) {
    const title = document.createElement("h3");
    title.textContent = "Скорректировать цены";
    title.style.marginTop = "14px";
    wrapper.appendChild(title);

    const editor = document.createElement("div");
    editor.className = "result-price-editor";

    editableItems.forEach((item, idx) => {
      const source = parsedLines[idx];
      const line = document.createElement("div");
      line.className = "price-editor-item";

      const name = document.createElement("div");
      const unitLabel = item.price_unit === "l" ? "за 1л" : "за 1кг";
      name.textContent = `${item.name} (${unitLabel})`;

      const input = document.createElement("input");
      input.type = "number";
      input.step = "0.01";
      input.min = "0";
      input.autocomplete = "off";
      input.inputMode = "decimal";
      input.value = String(item.price_value ?? 0);
      input.dataset.name = source.name;
      input.dataset.quantity = source.quantity;

      line.append(name, input);
      editor.appendChild(line);
    });

    const actions = document.createElement("div");
    actions.className = "inline-actions";

    const saveAllBtn = document.createElement("button");
    saveAllBtn.type = "button";
    saveAllBtn.textContent = "Сохранить цены и пересчитать";

    const status = document.createElement("div");
    status.className = "muted";

    saveAllBtn.addEventListener("click", async () => {
      try {
        const nextLines = [];
        const inputs = Array.from(editor.querySelectorAll("input[data-name]"));
        inputs.forEach((input) => {
          const nameValue = String(input.dataset.name || "").trim();
          const quantityValue = String(input.dataset.quantity || "").trim();
          const priceValue = Number(input.value);
          if (!nameValue || !quantityValue) {
            throw new Error("Не удалось собрать строку для пересчёта.");
          }
          if (!Number.isFinite(priceValue) || priceValue < 0) {
            throw new Error(`Некорректная цена у «${nameValue}».`);
          }
          nextLines.push(`${nameValue} | ${quantityValue} | ${priceValue}`);
        });

        if (!nextLines.length) {
          throw new Error("Нет цен для сохранения.");
        }

        saveAllBtn.disabled = true;
        setResult(status, "Сохраняем и пересчитываем...");

        const linesText = nextLines.join("\n");
        const linesField = el.customCalcForm.elements.namedItem("lines");
        if (linesField) {
          linesField.value = linesText;
        }

        const weightField = el.customCalcForm.elements.namedItem("weight");
        const weight = Number(weightField ? weightField.value : 0);
        const result = await api("/api/calc/custom", {
          method: "POST",
          body: {
            user_id: state.userId,
            weight_kg: weight,
            lines: linesText,
          },
        });

        state.lastCustomLines = linesText;
        renderCustomResult(result, linesText);
        showToast("Цены сохранены и расчёт обновлён.");
      } catch (error) {
        setResult(status, error.message, true);
      } finally {
        saveAllBtn.disabled = false;
      }
    });

    actions.append(saveAllBtn, status);
    wrapper.append(editor, actions);
  }

  setHtml(el.customCalcResult, "");
  clearErrorTextState(el.customCalcResult);
  el.customCalcResult.classList.add("muted");
  el.customCalcResult.appendChild(wrapper);
  state.lastCustomCalc = data;
  if (sourceLinesText) {
    state.lastCustomLines = sourceLinesText;
  }
}

async function loadCategoriesAndRecipes() {
  const categories = await api("/api/categories");
  state.categories = categories;
  state.sausagesByCategory.clear();
  categories.forEach((category) => {
    state.sausagesByCategory.set(category.slug, Array.isArray(category.items) ? category.items : []);
  });

  setHtml(el.categorySelect, "");
  categories.forEach((category, idx) => {
    const opt = document.createElement("option");
    opt.value = category.slug;
    opt.textContent = `${category.title} (${category.sausages_count})`;
    if (idx === 0) opt.selected = true;
    el.categorySelect.appendChild(opt);
  });

  if (categories.length === 0) {
    el.recipeTitle.textContent = "Рецепты отсутствуют";
    el.recipeText.textContent = "Категории пока не заполнены.";
    clearErrorTextState(el.recipeText);
    if (el.recipeImage) {
      el.recipeImage.classList.add("hidden");
      el.recipeImage.removeAttribute("src");
    }
    return;
  }

  state.selectedCategory = categories[0].slug;
  await renderRecipeSelectorForCurrentFilters();
}

async function loadSausagesForCategory(categorySlug) {
  const isCurrent = () => state.selectedCategory === categorySlug
    && !normalizedRecipeSearchText(state.recipeSearchQuery);
  let data;
  try {
    data = await api(`/api/categories/${encodeURIComponent(categorySlug)}`);
  } catch (error) {
    if (isCurrent()) {
      state.currentRecipe = null;
      throw error;
    }
    return;
  }
  const items = Array.isArray(data.items) ? data.items : [];
  state.sausagesByCategory.set(categorySlug, items);
  if (!isCurrent()) return;
  await renderRecipeSelectorForCurrentFilters();
}

function normalizedRecipeSearchText(value) {
  return String(value || "").trim().toLowerCase();
}

function visibleRecipeItemsForCurrentFilters() {
  const search = normalizedRecipeSearchText(state.recipeSearchQuery);
  const categories = search
    ? state.categories
    : state.categories.filter((category) => category.slug === state.selectedCategory);
  const items = categories.flatMap((category) => (
    (state.sausagesByCategory.get(category.slug) || []).map((item) => ({
      ...item,
      category_slug: category.slug,
      category_title: category.title,
    }))
  ));
  const seen = new Set();
  return items.filter((item) => {
    const haystack = normalizedRecipeSearchText(`${item?.display_name || ""} ${item?.slug || ""}`);
    const matches = !search || haystack.includes(search);
    if (!matches || seen.has(item.slug)) return false;
    seen.add(item.slug);
    return true;
  });
}

function selectRecipeCategory(item) {
  if (!item?.category_slug) return;
  state.selectedCategory = item.category_slug;
  el.categorySelect.value = item.category_slug;
}

async function renderRecipeSelectorForCurrentFilters() {
  const visibleItems = visibleRecipeItemsForCurrentFilters();
  const search = normalizedRecipeSearchText(state.recipeSearchQuery);
  const previousSausageSlug = String(state.selectedSausage || "").trim();
  const keepCurrentSelection = visibleItems.some((item) => String(item?.slug || "").trim() === previousSausageSlug);
  const defaultSausageSlug = keepCurrentSelection
    ? previousSausageSlug
    : String(visibleItems?.[0]?.slug || "").trim();

  setHtml(el.sausageSelect, "");
  visibleItems.forEach((item) => {
    const opt = document.createElement("option");
    opt.value = item.slug;
    opt.textContent = search ? `${item.display_name} — ${item.category_title}` : item.display_name;
    opt.selected = String(item?.slug || "").trim() === defaultSausageSlug;
    el.sausageSelect.appendChild(opt);
  });

  if (visibleItems.length === 0) {
    state.selectedSausage = "";
    state.currentRecipe = null;
    state.lastRecipeCalc = null;
    setHtml(el.recipeCalcResult, "");
    if (el.recipeCartHint) {
      setHtml(el.recipeCartHint, "");
      el.recipeCartHint.classList.add("hidden");
    }
    el.recipeTitle.textContent = "Сорт не найден";
    el.recipeText.textContent = search
      ? "По этому поиску рецептов не найдено."
      : "В этой категории нет сортов.";
    clearErrorTextState(el.recipeText);
    setHtml(el.recipeIngredients, "");
    if (el.recipeImage) {
      el.recipeImage.classList.add("hidden");
      el.recipeImage.removeAttribute("src");
    }
    return;
  }

  state.selectedSausage = defaultSausageSlug || String(visibleItems?.[0]?.slug || "").trim();
  selectRecipeCategory(visibleItems.find((item) => item.slug === state.selectedSausage));
  if (state.currentRecipe?.slug === state.selectedSausage) return;

  try {
    await loadRecipe(state.selectedSausage);
  } catch (error) {
    setResult(el.recipeText, error.message || "Не удалось загрузить рецепт.", true);
  }
}

async function onSausageChange(sausageSlug) {
  state.selectedSausage = sausageSlug;
  const selected = visibleRecipeItemsForCurrentFilters().find((item) => item.slug === sausageSlug);
  selectRecipeCategory(selected);
  if (selected) {
    trackClick("sausage", selected.slug, selected.display_name);
  }
  await loadRecipe(sausageSlug);
}

async function loadHandbook() {
  const sections = await api("/api/handbook");
  state.handbookSections = sections;

  setHtml(el.handbookSectionSelect, "");
  sections.forEach((section, idx) => {
    const opt = document.createElement("option");
    opt.value = section.key;
    opt.textContent = section.title;
    if (idx === 0) opt.selected = true;
    el.handbookSectionSelect.appendChild(opt);
  });

  if (sections.length === 0) {
    el.handbookArticleTitle.textContent = "Справочник пуст";
    el.handbookArticleText.textContent = "";
    return;
  }

  renderHandbookArticles(sections[0].key);
}

function renderHandbookArticles(sectionKey) {
  const section = state.handbookSections.find((item) => item.key === sectionKey);
  setHtml(el.handbookArticleSelect, "");

  if (!section || !Array.isArray(section.articles) || section.articles.length === 0) {
    el.handbookArticleTitle.textContent = "Нет статей";
    el.handbookArticleText.textContent = "В этом разделе пока нет материалов.";
    el.handbookArticleLink.classList.add("hidden");
    return;
  }

  section.articles.forEach((article, idx) => {
    const opt = document.createElement("option");
    opt.value = article.key;
    opt.textContent = article.title;
    if (idx === 0) opt.selected = true;
    el.handbookArticleSelect.appendChild(opt);
  });

  renderHandbookArticle(section.articles[0]);
}

function renderHandbookArticle(article) {
  el.handbookArticleTitle.textContent = article.title || "Статья";
  el.handbookArticleText.textContent = article.text || "";

  const articleLink = safePublicSocialUrl(article.link);
  if (articleLink) {
    el.handbookArticleLink.classList.remove("hidden");
    el.handbookArticleLink.href = articleLink;
    el.handbookArticleLink.textContent = "Открыть ссылку";
  } else {
    el.handbookArticleLink.classList.add("hidden");
    el.handbookArticleLink.href = "#";
    el.handbookArticleLink.textContent = "";
  }
}

function articleIdFromUrl() {
  const params = new URLSearchParams(window.location.search || "");
  return Number(params.get("article") || params.get("article_id") || 0);
}

function pluralRu(value, forms) {
  const number = Math.abs(Number(value || 0)) % 100;
  const tail = number % 10;
  if (number > 10 && number < 20) return forms[2];
  if (tail === 1) return forms[0];
  if (tail >= 2 && tail <= 4) return forms[1];
  return forms[2];
}

function commentIdFromUrl() {
  const params = new URLSearchParams(window.location.search || "");
  return Number(params.get("comment") || params.get("comment_id") || 0);
}

function articleShareUrl(articleId) {
  const id = Number(articleId || 0);
  const url = new URL("/", window.location.origin);
  url.searchParams.set("article", String(id));
  url.hash = "articles";
  return url.toString();
}

async function copyArticleLink(article, button) {
  const url = articleShareUrl(article?.id);
  const previous = button?.textContent || "Поделиться";
  if (button) {
    button.disabled = true;
    button.textContent = "Копируем...";
  }
  try {
    const copied = await copyTextToClipboard(url);
    if (!copied) throw new Error("copy_failed");
    showToast("Ссылка на статью скопирована.");
    if (button) button.textContent = "Скопировано";
  } catch (_error) {
    showToast("Не удалось скопировать ссылку. Скопируйте адрес из браузера.", true);
    if (button) button.textContent = previous;
  } finally {
    if (button) {
      setTimeout(() => {
        button.disabled = false;
        button.textContent = previous;
      }, 1400);
    }
  }
}

async function loadArticles({ selectId = 0 } = {}) {
  if (!el.articlesList) return;
  const data = await api("/api/articles");
  state.articles = Array.isArray(data.items) ? data.items : [];
  renderArticlesList();
  const requestedId = Number(selectId || state.selectedArticleId || 0);
  const initial = state.articles.find((item) => Number(item.id) === requestedId) || state.articles[0];
  if (initial) {
    await openArticle(initial.id, {
      markRead: Number(selectId || 0) > 0 || state.currentTab === "articles",
      focusCommentId: Number(selectId || 0) > 0 ? commentIdFromUrl() : 0,
      reveal: Number(selectId || 0) > 0,
    });
  } else {
    state.selectedArticleId = 0;
    state.articleDetail = null;
    el.articleEmpty?.classList.remove("hidden");
    el.articleView?.classList.add("hidden");
  }
}

function renderArticlesList() {
  if (!el.articlesList) return;
  setHtml(el.articlesList, "");
  if (!state.articles.length) {
    el.articlesList.classList.add("muted");
    el.articlesList.textContent = "Опубликованных статей пока нет.";
    return;
  }
  el.articlesList.classList.remove("muted");
  state.articles.forEach((item) => {
    const card = document.createElement("button");
    card.type = "button";
    card.className = `article-list-card${Number(item.id) === Number(state.selectedArticleId) ? " is-active" : ""}`;
    card.dataset.articleId = String(item.id || 0);
    const cardImageUrl = item.card_image_url || item.cover_image_url || "";
    if (cardImageUrl) {
      const image = document.createElement("img");
      image.className = "article-list-card__image";
      image.src = cardImageUrl;
      image.alt = "";
      image.loading = "lazy";
      card.appendChild(image);
    }
    const body = document.createElement("span");
    body.className = "article-list-card__body";
    setHtml(body, `
      <strong>${escapeHtml(item.title || "Статья")}${item.is_premium ? '<span class="article-premium-badge">PREMIUM</span>' : ""}${item.is_new ? '<span class="article-new-badge">NEW</span>' : ""}</strong>
      <span>${escapeHtml(item.excerpt || "")}</span>
      <small class="article-list-card__meta">
        <span>${escapeHtml(formatMskDateTimeLabel(item.published_at || item.updated_at, ""))}</span>
        <span>👍 ${Number(item.likes || 0)}</span>
        <span>💬 ${Number(item.comments_count || 0)}</span>
      </small>
    `);
    card.appendChild(body);
    card.addEventListener("click", () => void openArticle(item.id, { markRead: true, reveal: true }));
    el.articlesList.appendChild(card);
  });
}

function isMobileTouchLayout() {
  return window.matchMedia("(max-width: 820px), (hover: none) and (pointer: coarse) and (max-width: 1024px) and (max-height: 600px)").matches;
}

function setArticleReadingMode(reading, { scroll = true } = {}) {
  el.articlesShell?.classList.toggle("is-reading", Boolean(reading));
  if (!scroll || !isMobileTouchLayout()) return;
  const target = reading ? el.articleDetail : el.articlesList;
  window.requestAnimationFrame(() => {
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    target?.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "start" });
  });
}

async function openArticle(articleId, { markRead = true, focusCommentId = 0, reveal = false } = {}) {
  const safeId = Number(articleId || 0);
  if (!safeId || !el.articleView) return;
  if (reveal) setArticleReadingMode(true);
  if (Number(state.selectedArticleId || 0) !== safeId) clearArticleReply();
  state.selectedArticleId = safeId;
  renderArticlesList();
  el.articleEmpty?.classList.remove("hidden");
  if (el.articleEmpty) el.articleEmpty.textContent = "Загрузка статьи…";
  el.articleView.classList.add("hidden");
  try {
    const suffix = markRead && state.isAuthenticated ? "?mark_read=1" : "";
    const data = await api(`/api/articles/${encodeURIComponent(String(safeId))}${suffix}`);
    state.articleDetail = data;
    if (markRead) {
      const summary = state.articles.find((item) => Number(item.id) === safeId);
      if (summary) summary.is_new = false;
    }
    renderArticleDetail();
    if (focusCommentId > 0) focusArticleComment(focusCommentId);
  } catch (error) {
    if (el.articleEmpty) el.articleEmpty.textContent = errorText(error, "Не удалось загрузить статью.");
    showToast(errorText(error, "Не удалось загрузить статью."), true);
  }
}

function syncArticleSummary(article, commentsCount = null) {
  const item = state.articles.find((row) => Number(row.id) === Number(article?.id || 0));
  if (!item) return;
  item.likes = Number(article.likes || 0);
  item.dislikes = Number(article.dislikes || 0);
  item.my_reaction = Number(article.my_reaction || 0);
  if (Object.prototype.hasOwnProperty.call(article, "is_new")) item.is_new = Boolean(article.is_new);
  if (commentsCount !== null) item.comments_count = Number(commentsCount || 0);
  renderArticlesList();
}

function activeArticleCommentsCount(comments) {
  return (Array.isArray(comments) ? comments : []).filter((comment) => !comment.is_deleted).length;
}

function renderArticleDetail() {
  const data = state.articleDetail || {};
  const article = data.article || null;
  if (!article || !el.articleView) return;
  el.articleEmpty?.classList.add("hidden");
  el.articleView.classList.remove("hidden");
  setHtml(el.articleTitle, `${escapeHtml(article.title || "Статья")}${article.is_premium ? '<span class="article-premium-badge">PREMIUM</span>' : ""}`);
  el.articlePublishedAt.textContent = formatMskDateTimeLabel(article.published_at || article.updated_at, "");
  el.articleAuthor.textContent = article.author_nickname ? `Автор: ${article.author_nickname}` : "";
  const premiumLocked = Boolean(data.premium_locked);
  if (premiumLocked) {
    const premiumPlansMarkup = String(el.accountPlans?.innerHTML || "").trim();
    setHtml(el.articleContent, `
      <section class="article-premium-gate">
        <span class="article-premium-badge">PREMIUM</span>
        <h3>Эта статья доступна подписчикам PREMIUM</h3>
        <p>${escapeHtml(article.excerpt || "Оформите PREMIUM, чтобы прочитать материал полностью.")}</p>
        ${premiumPlansMarkup
          ? `<div class="paywall-grid article-premium-gate__plans">${premiumPlansMarkup}</div>`
          : `<button type="button" data-open-premium-plans>${state.isAuthenticated ? "Открыть форму оформления PREMIUM" : "Войти и оформить PREMIUM"}</button>`}
        ${state.isAuthenticated ? `
          <div class="payment-consent article-premium-gate__consent" aria-labelledby="articlePremiumConsentHeading">
            <p id="articlePremiumConsentHeading" class="payment-consent__lead">
              Оформление оплаты — это <strong>подписка</strong> с автопродлением раз в <strong>30 дней</strong>. Отметьте все пункты перед оплатой.
            </p>
            <label class="payment-consent__row">
              <input type="checkbox" id="artAcceptPublicOffer" autocomplete="off">
              <span>Я принимаю <a href="/public-offer" target="_blank" rel="noopener">публичную оферту</a>, включая условия регулярных списаний.</span>
            </label>
            <label class="payment-consent__row">
              <input type="checkbox" id="artAcceptSubscriptionAgreement" autocomplete="off">
              <span>Я принимаю <a href="/subscription-agreement" target="_blank" rel="noopener">соглашение о подписке</a>.</span>
            </label>
            <label class="payment-consent__row">
              <input type="checkbox" id="artAcceptPaymentCredentials" autocomplete="off">
              <span>Я принимаю <a href="/payment-credentials" target="_blank" rel="noopener">условия сохранения платёжных данных</a> для рекуррентных платежей.</span>
            </label>
            <label class="payment-consent__row">
              <input type="checkbox" id="artAcceptPrivacyPolicy" autocomplete="off">
              <span>Я принимаю <a href="/privacy-policy" target="_blank" rel="noopener">согласие на обработку персональных данных</a>.</span>
            </label>
          </div>
        ` : ""}
      </section>
    `);
    el.articleContent.querySelectorAll("button[data-account-tier]").forEach((button) => {
      button.addEventListener("click", async () => {
        const nextTier = String(button.dataset.accountTier || "premium").trim().toLowerCase();
        await startTbankPayment(nextTier, button);
      });
    });
    el.articleContent.querySelector("[data-open-premium-plans]")?.addEventListener("click", () => {
      if (!state.isAuthenticated) {
        redirectToAuth("register");
        return;
      }
      activateTab("account");
      document.getElementById("accountPlans")?.scrollIntoView({ behavior: "smooth", block: "center" });
    });
  } else {
    setHtml(el.articleContent, article.content_html || "");
  }
  if (article.cover_image_url) {
    el.articleCover.src = article.cover_image_url;
    el.articleCover.alt = article.title || "Обложка статьи";
    el.articleCover.classList.remove("hidden");
  } else {
    el.articleCover.classList.add("hidden");
    el.articleCover.removeAttribute("src");
  }
  const likeCount = el.articleLikeBtn?.querySelector("span");
  const dislikeCount = el.articleDislikeBtn?.querySelector("span");
  if (likeCount) likeCount.textContent = String(Number(article.likes || 0));
  if (dislikeCount) dislikeCount.textContent = String(Number(article.dislikes || 0));
  el.articleLikeBtn?.classList.toggle("is-active", Number(article.my_reaction || 0) === 1);
  el.articleDislikeBtn?.classList.toggle("is-active", Number(article.my_reaction || 0) === -1);
  if (el.articleLikeBtn) el.articleLikeBtn.disabled = !state.isAuthenticated;
  if (el.articleDislikeBtn) el.articleDislikeBtn.disabled = !state.isAuthenticated;
  el.articleLikeBtn?.closest(".article-reactions")?.classList.toggle("hidden", premiumLocked);
  el.articleCommentsList?.closest(".article-comments")?.classList.toggle("hidden", premiumLocked);
  el.articleCommentForm?.classList.toggle("hidden", premiumLocked || !state.isAuthenticated);
  el.articleCommentLoginHint?.classList.toggle("hidden", premiumLocked || state.isAuthenticated);
  if (!premiumLocked) renderArticleComments(Array.isArray(data.comments) ? data.comments : []);
  syncArticleSummary(article, premiumLocked ? null : activeArticleCommentsCount(data.comments));
}

function clearArticleReply() {
  state.articleReply?.form?.remove();
  state.articleReply = { parentId: 0, nickname: "", form: null };
}

function clearArticleCommentEditor() {
  state.articleCommentEditor?.form?.remove();
  state.articleCommentEditor = { commentId: 0, form: null };
}

async function publishArticleComment(text, parentCommentId = 0) {
  const article = state.articleDetail?.article;
  if (!article) throw new Error("Статья не выбрана.");
  const data = await api(`/api/articles/${article.id}/comments`, {
    method: "POST",
    body: { text, parent_comment_id: Number(parentCommentId || 0) },
  });
  state.articleDetail.comments = [...(state.articleDetail.comments || []), data.comment];
  state.articleReply = { parentId: 0, nickname: "", form: null };
  renderArticleComments(state.articleDetail.comments);
  syncArticleSummary(article, activeArticleCommentsCount(state.articleDetail.comments));
  return data.comment;
}

function startArticleReply(comment, commentRow) {
  if (!state.isAuthenticated) {
    redirectToAuth("login");
    return;
  }
  if (!commentRow) return;
  clearArticleReply();
  clearArticleCommentEditor();
  const nickname = String(comment?.nickname || "Пользователь").trim();
  const parentId = Number(comment?.id || 0);
  const form = document.createElement("form");
  form.className = "article-inline-reply-form";
  form.setAttribute("aria-label", `Ответ пользователю ${nickname}`);
  setHtml(form, `
    <label class="field">
      <span>Ответ для ${escapeHtml(nickname)}</span>
      <textarea rows="2" maxlength="5000" placeholder="Напишите ответ"></textarea>
    </label>
    <div class="article-inline-reply-form__actions">
      <button type="submit" class="chip mini">Отправить</button>
      <button type="button" class="chip mini" data-reply-cancel>Отменить</button>
    </div>
  `);
  state.articleReply = { parentId, nickname, form };
  commentRow.after(form);
  const textarea = form.querySelector("textarea");
  const prefix = `${nickname}, `;
  textarea.value = prefix;
  textarea.focus();
  textarea.setSelectionRange(textarea.value.length, textarea.value.length);
  form.querySelector("[data-reply-cancel]")?.addEventListener("click", clearArticleReply);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const text = String(textarea.value || "").trim();
    const prefixWithoutSpace = `${nickname},`;
    const replyBody = text.toLocaleLowerCase().startsWith(prefixWithoutSpace.toLocaleLowerCase())
      ? text.slice(prefixWithoutSpace.length).trim()
      : text;
    if (replyBody.length < 2) {
      showToast("Напишите ответ после имени.", true);
      return;
    }
    const submitButton = form.querySelector('button[type="submit"]');
    setButtonBusy(submitButton, true, "Отправляем…");
    try {
      const created = await publishArticleComment(text, parentId);
      showToast("Ответ опубликован.");
      focusArticleComment(created.id);
    } catch (error) {
      showToast(errorText(error, "Не удалось отправить ответ."), true);
      setButtonBusy(submitButton, false);
    }
  });
}

function startArticleCommentEdit(comment, commentRow) {
  if (!state.isAuthenticated || Number(comment?.user_id || 0) !== Number(state.userId || 0) || comment?.is_deleted) return;
  clearArticleReply();
  clearArticleCommentEditor();
  const commentId = Number(comment.id || 0);
  const form = document.createElement("form");
  form.className = "article-inline-reply-form article-inline-edit-form";
  form.setAttribute("aria-label", "Редактирование комментария");
  setHtml(form, `
    <label class="field">
      <span>Редактирование комментария</span>
      <textarea rows="3" maxlength="5000"></textarea>
    </label>
    <div class="article-inline-reply-form__actions">
      <button type="submit" class="chip mini">Сохранить</button>
      <button type="button" class="chip mini" data-edit-cancel>Отменить</button>
    </div>
  `);
  state.articleCommentEditor = { commentId, form };
  commentRow.after(form);
  const textarea = form.querySelector("textarea");
  textarea.value = String(comment.text || "");
  textarea.focus();
  textarea.setSelectionRange(textarea.value.length, textarea.value.length);
  form.querySelector("[data-edit-cancel]")?.addEventListener("click", clearArticleCommentEditor);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const text = String(textarea.value || "").trim();
    const replyBody = Number(comment.parent_comment_id || 0) > 0 && text.includes(",")
      ? text.slice(text.indexOf(",") + 1).trim()
      : text;
    if (replyBody.length < 2) {
      showToast(Number(comment.parent_comment_id || 0) > 0 ? "Напишите ответ после имени." : "Комментарий должен содержать минимум 2 символа.", true);
      return;
    }
    const submitButton = form.querySelector('button[type="submit"]');
    setButtonBusy(submitButton, true, "Сохраняем…");
    try {
      const article = state.articleDetail?.article;
      const data = await api(`/api/articles/${article.id}/comments/${commentId}`, {
        method: "PATCH",
        body: { text },
      });
      const current = (state.articleDetail.comments || []).find((item) => Number(item.id) === commentId);
      if (current) Object.assign(current, data.comment);
      state.articleCommentEditor = { commentId: 0, form: null };
      renderArticleComments(state.articleDetail.comments || []);
      showToast("Комментарий изменён.");
      focusArticleComment(commentId);
    } catch (error) {
      showToast(errorText(error, "Не удалось изменить комментарий."), true);
      setButtonBusy(submitButton, false);
    }
  });
}

async function deleteOwnArticleComment(comment) {
  const article = state.articleDetail?.article;
  const commentId = Number(comment?.id || 0);
  const isOwner = Number(comment?.user_id || 0) === Number(state.userId || 0);
  if (!article || commentId <= 0 || (!isOwner && !state.isAdminAccount)) return;
  const confirmation = isOwner
    ? "Удалить этот комментарий? Ответы других пользователей останутся в ветке."
    : `Удалить комментарий пользователя «${String(comment?.nickname || "Пользователь")}»? Ответы останутся в ветке.`;
  if (!window.confirm(confirmation)) return;
  try {
    const data = await api(`/api/articles/${article.id}/comments/${commentId}`, { method: "DELETE" });
    state.articleDetail.comments = Array.isArray(data.comments) ? data.comments : [];
    clearArticleReply();
    clearArticleCommentEditor();
    renderArticleComments(state.articleDetail.comments);
    syncArticleSummary(article, Number(data.comments_count || 0));
    showToast("Комментарий удалён.");
  } catch (error) {
    showToast(errorText(error, "Не удалось удалить комментарий."), true);
  }
}

function focusArticleComment(commentId) {
  const id = Number(commentId || 0);
  if (id <= 0) return;
  window.requestAnimationFrame(() => {
    const target = document.getElementById(`article-comment-${id}`);
    if (!target) return;
    target.scrollIntoView({ behavior: "smooth", block: "center" });
    target.classList.add("is-highlighted");
    window.setTimeout(() => target.classList.remove("is-highlighted"), 2600);
  });
}

function renderArticleComments(comments) {
  if (!el.articleCommentsList) return;
  clearArticleReply();
  clearArticleCommentEditor();
  const items = Array.isArray(comments) ? comments : [];
  const activeCommentsCount = activeArticleCommentsCount(items);
  if (el.articleCommentsCount) el.articleCommentsCount.textContent = String(activeCommentsCount);
  setHtml(el.articleCommentsList, "");
  if (!items.length) {
    el.articleCommentsList.classList.add("muted");
    el.articleCommentsList.textContent = "Комментариев пока нет.";
    return;
  }
  el.articleCommentsList.classList.remove("muted");
  const byParent = new Map();
  const knownIds = new Set(items.map((comment) => Number(comment.id || 0)));
  items.forEach((comment) => {
    const rawParentId = Number(comment.parent_comment_id || 0);
    const parentId = rawParentId > 0 && knownIds.has(rawParentId) ? rawParentId : 0;
    if (!byParent.has(parentId)) byParent.set(parentId, []);
    byParent.get(parentId).push(comment);
  });
  const focusedId = commentIdFromUrl();
  const subtreeContains = (commentId, targetId) => {
    if (Number(commentId) === Number(targetId)) return true;
    return (byParent.get(Number(commentId)) || []).some((child) => subtreeContains(child.id, targetId));
  };
  const buildThread = (comment, depth = 0) => {
    const thread = document.createElement("section");
    thread.className = "article-comment-thread";
    thread.dataset.depth = String(depth);
    const row = document.createElement("article");
    const isDeleted = Boolean(comment.is_deleted);
    const isOwner = !isDeleted && state.isAuthenticated && Number(comment.user_id || 0) === Number(state.userId || 0);
    const canDelete = !isDeleted && state.isAuthenticated && (isOwner || state.isAdminAccount);
    row.className = `article-comment${isDeleted ? " is-deleted" : ""}`;
    row.id = `article-comment-${Number(comment.id || 0)}`;
    if (isDeleted) {
      setHtml(row, '<p class="article-comment__deleted-label">Комментарий удалён</p>');
    } else {
      setHtml(row, `
        <header>
          <strong>${escapeHtml(comment.nickname || "Пользователь")}${comment.is_admin ? '<span class="article-comment__admin-badge">ADMIN</span>' : ""}${comment.is_premium ? '<span class="article-comment__premium-badge">PREMIUM</span>' : ""}</strong>
          <time>${escapeHtml(formatMskDateTimeLabel(comment.created_at, ""))}${comment.edited_at ? " · изменено" : ""}</time>
        </header>
        <p>${escapeHtml(comment.text || "")}</p>
        <div class="article-comment__reactions">
          <button type="button" class="reaction-btn mini${Number(comment.my_reaction || 0) === 1 ? " is-active" : ""}" data-comment-reaction="1" ${state.isAuthenticated ? "" : "disabled"}>👍 <span>${Number(comment.likes || 0)}</span></button>
          <button type="button" class="reaction-btn mini${Number(comment.my_reaction || 0) === -1 ? " is-active" : ""}" data-comment-reaction="-1" ${state.isAuthenticated ? "" : "disabled"}>👎 <span>${Number(comment.dislikes || 0)}</span></button>
          <button type="button" class="article-comment__reply chip mini" data-comment-reply ${state.isAuthenticated ? "" : "disabled"}>Ответить</button>
          ${isOwner ? '<button type="button" class="chip mini" data-comment-edit>Редактировать</button>' : ""}
          ${canDelete ? `<button type="button" class="chip mini article-comment__delete" data-comment-delete>${isOwner ? "Удалить" : "Удалить (админ)"}</button>` : ""}
        </div>
      `);
      row.querySelectorAll("[data-comment-reaction]").forEach((button) => {
        button.addEventListener("click", () => void reactToArticleComment(comment.id, Number(button.dataset.commentReaction)));
      });
      row.querySelector("[data-comment-reply]")?.addEventListener("click", () => startArticleReply(comment, row));
      row.querySelector("[data-comment-edit]")?.addEventListener("click", () => startArticleCommentEdit(comment, row));
      row.querySelector("[data-comment-delete]")?.addEventListener("click", () => void deleteOwnArticleComment(comment));
    }
    thread.appendChild(row);
    const children = byParent.get(Number(comment.id || 0)) || [];
    if (children.length) {
      const childrenWrap = document.createElement("div");
      childrenWrap.className = "article-comment-children";
      const forceExpanded = focusedId > 0 && subtreeContains(comment.id, focusedId);
      const hiddenCount = forceExpanded ? 0 : Math.max(0, children.length - 2);
      if (hiddenCount > 0) {
        const hiddenWrap = document.createElement("div");
        hiddenWrap.className = "article-comment-children__hidden hidden";
        children.slice(0, hiddenCount).forEach((child) => hiddenWrap.appendChild(buildThread(child, depth + 1)));
        const toggle = document.createElement("button");
        toggle.type = "button";
        toggle.className = "article-replies-toggle chip mini";
        toggle.textContent = `Показать ещё ${hiddenCount} ${pluralRu(hiddenCount, ["ответ", "ответа", "ответов"])}`;
        toggle.addEventListener("click", () => {
          const opening = hiddenWrap.classList.contains("hidden");
          hiddenWrap.classList.toggle("hidden", !opening);
          toggle.textContent = opening
            ? "Свернуть ответы"
            : `Показать ещё ${hiddenCount} ${pluralRu(hiddenCount, ["ответ", "ответа", "ответов"])}`;
        });
        childrenWrap.appendChild(toggle);
        childrenWrap.appendChild(hiddenWrap);
      }
      children.slice(hiddenCount).forEach((child) => childrenWrap.appendChild(buildThread(child, depth + 1)));
      thread.appendChild(childrenWrap);
    }
    return thread;
  };
  (byParent.get(0) || []).forEach((comment) => el.articleCommentsList.appendChild(buildThread(comment)));
  if (focusedId > 0) focusArticleComment(focusedId);
}

async function markCurrentArticleRead() {
  const article = state.articleDetail?.article;
  if (!article || !state.isAuthenticated) return;
  const summary = state.articles.find((item) => Number(item.id) === Number(article.id));
  if (!summary?.is_new && !article.is_new) return;
  summary.is_new = false;
  article.is_new = false;
  renderArticlesList();
  try {
    await api(`/api/articles/${article.id}/read`, { method: "POST", body: {} });
  } catch (_error) {
    summary.is_new = true;
    article.is_new = true;
    renderArticlesList();
  }
}

async function reactToArticle(reaction) {
  const article = state.articleDetail?.article;
  if (!article) return;
  if (!state.isAuthenticated) {
    redirectToAuth("login");
    return;
  }
  try {
    const summary = await api(`/api/articles/${article.id}/reaction`, {
      method: "PUT",
      body: { reaction },
    });
    Object.assign(article, summary);
    renderArticleDetail();
  } catch (error) {
    showToast(errorText(error, "Не удалось сохранить оценку."), true);
  }
}

async function reactToArticleComment(commentId, reaction) {
  const article = state.articleDetail?.article;
  if (!article || !state.isAuthenticated) return;
  try {
    const summary = await api(`/api/articles/${article.id}/comments/${commentId}/reaction`, {
      method: "PUT",
      body: { reaction },
    });
    const comment = (state.articleDetail.comments || []).find((row) => Number(row.id) === Number(commentId));
    if (comment) Object.assign(comment, summary);
    renderArticleComments(state.articleDetail.comments || []);
  } catch (error) {
    showToast(errorText(error, "Не удалось сохранить оценку комментария."), true);
  }
}

async function loadEquipment() {
  const items = await api("/api/equipment");
  state.equipment = items;
  setHtml(el.equipmentGrid, "");

  if (!Array.isArray(items) || items.length === 0) {
    const div = document.createElement("div");
    div.className = "muted";
    div.textContent = "Пока нет позиций оборудования.";
    el.equipmentGrid.appendChild(div);
    return;
  }

  items.forEach((item) => {
    const card = document.createElement("article");
    card.className = "equipment-card";

    if (item.photo_url) {
      const img = document.createElement("img");
      img.src = item.photo_url;
      img.alt = item.title || "Оборудование";
      img.loading = "lazy";
      card.appendChild(img);
    }

    const title = document.createElement("h3");
    title.textContent = item.title;

    const meta = document.createElement("p");
    meta.textContent = item.category_name || "Оборудование";

    card.append(title, meta);
    card.addEventListener("click", () => openEquipmentDialog(item));
    el.equipmentGrid.appendChild(card);
  });
}
function resetEquipmentDialogScroll() {
  if (!el.equipmentDialog) return;
  el.equipmentDialog.scrollTop = 0;
  const scrollSurface = el.equipmentDialog.querySelector(":scope > .card");
  if (scrollSurface) scrollSurface.scrollTop = 0;
}

function resetEquipmentDialogScrollAfterLayout() {
  resetEquipmentDialogScroll();
  if (typeof window.requestAnimationFrame === "function") {
    window.requestAnimationFrame(resetEquipmentDialogScroll);
  }
}

function openEquipmentDialog(item) {
  resetEquipmentDialogScroll();
  el.equipmentDialog.classList.remove("dialog--store-product");
  el.equipmentDialog.classList.remove("dialog--store-product-no-image");
  el.equipmentDialog.removeAttribute("data-product-id");
  if (el.equipmentDialogShare) {
    el.equipmentDialogShare.classList.add("hidden");
    el.equipmentDialogShare.onclick = null;
    el.equipmentDialogShare.textContent = "Поделиться";
  }
  if (el.equipmentDialogReviews) {
    setHtml(el.equipmentDialogReviews, "");
    el.equipmentDialogReviews.classList.add("hidden");
  }
  el.equipmentDialogTitle.textContent = item.title || "Оборудование";
  el.equipmentDialogText.textContent = item.description || "Описание отсутствует.";
  clearStoreRelatedDialog();

  if (item.photo_url) {
    el.equipmentDialogImage.src = item.photo_url;
    el.equipmentDialogImage.classList.remove("hidden");
  } else {
    el.equipmentDialogImage.classList.add("hidden");
    el.equipmentDialogImage.removeAttribute("src");
  }

  const buyLink = safePublicSocialUrl(item.buy_link);
  if (buyLink) {
    el.equipmentDialogLink.classList.remove("hidden");
    el.equipmentDialogLink.href = buyLink;
    el.equipmentDialogLink.target = "_blank";
    el.equipmentDialogLink.rel = "noopener";
    el.equipmentDialogLink.removeAttribute("role");
    el.equipmentDialogLink.removeAttribute("tabindex");
    el.equipmentDialogLink.textContent = item.buy_label || "Купить";
    el.equipmentDialogLink.onclick = () => {
      trackClick("ad", `equipment_${item.id}`, item.title || "Оборудование");
    };
  } else {
    el.equipmentDialogLink.classList.add("hidden");
    el.equipmentDialogLink.href = "#";
    el.equipmentDialogLink.textContent = "";
    el.equipmentDialogLink.removeAttribute("target");
    el.equipmentDialogLink.removeAttribute("rel");
    el.equipmentDialogLink.removeAttribute("role");
    el.equipmentDialogLink.removeAttribute("tabindex");
    el.equipmentDialogLink.onclick = null;
  }

  if (typeof el.equipmentDialog.showModal === "function" && !el.equipmentDialog.open) {
    el.equipmentDialog.showModal();
  }
  resetEquipmentDialogScrollAfterLayout();
}

// Store frontend/admin module
state.store = { products: [], categories: [], deliveryMethods: [], deliverySizes: [], cart: [], categoryFilter: "", searchQuery: "", promoSettings: {}, reviewBonusSettings: {}, referralSettings: {}, rewards: {sausages_kopecks:0}, useSausages: false, premiumActive: false, paidPremiumCashback: false, premiumDiscountPercent: 0, promoBanner: {}, promoCarouselIndex: 0, ozonDelivery: { enabled:false, pointsReady:false, checkoutEnabled:false, selectedPoint:null, quote:null, quoteFingerprint:"", estimate:null, estimatePointId:0, estimateSeq:0, searchSeq:0 } };
state.admin.storeProducts = [];
state.admin.storeDeliveryMethods = [];
state.admin.storeDeliverySizes = [];
state.admin.storePromoSettings = {};
state.admin.reviewBonusSettings = {};
state.admin.referralSettings = {};
state.admin.storeSettings = {};
state.admin.storeCategories = [];
state.admin.selectedStoreCategoryId = "";
state.admin.relatedProductIds = [];
let storeCartSaveTimer = null;
let storeCartSaveSeq = 0;
let storeSearchTimer = null;
let storePromoCarouselTimer = null;
const STORE_CHECKOUT_ATTEMPT_STORAGE_KEY = "kolbabot_store_checkout_attempt_v1";
const STORE_CHECKOUT_ATTEMPT_MAX_AGE_MS = 24 * 60 * 60 * 1000;
let storeCheckoutSubmitting = false;
let storeCheckoutNavigating = false;
function resetStoreCheckoutSubmissionOnPageShow(event){
  // Back may restore this same JS context from the browser's page cache.
  // Only a completed handoff unlocks here; an unresolved POST keeps its key.
  if(!event?.persisted || !storeCheckoutNavigating) return;
  storeCheckoutNavigating=false;
  storeCheckoutSubmitting=false;
}
window.addEventListener('pageshow', resetStoreCheckoutSubmissionOnPageShow);
function storeCheckoutAttemptIntent(body){
  return JSON.stringify({
    customer_name:String(body?.customer_name||''),
    phone:String(body?.phone||''),
    address:String(body?.address||''),
    digital_delivery_email:String(body?.digital_delivery_email||''),
    delivery_method:String(body?.delivery_method||''),
    ozon_delivery_point_id:String(body?.ozon_delivery_point_id||''),
    ozon_manual_pvz_address:String(body?.ozon_manual_pvz_address||''),
    ozon_manual_pvz_name:String(body?.ozon_manual_pvz_name||''),
    use_sausages:Boolean(body?.use_sausages),
    items:(body?.items||[]).map((item)=>({product_id:Number(item?.product_id||0),quantity:Number(item?.quantity||0)})).sort((a,b)=>a.product_id-b.product_id||a.quantity-b.quantity),
  });
}
function newStoreCheckoutIdempotencyKey(){
  const uuid=window.crypto?.randomUUID?.();
  const fallback=`${Date.now().toString(36)}-${Math.random().toString(36).slice(2,18)}`;
  return `checkout-${uuid||fallback}`;
}
function clearStoreCheckoutAttempt(expectedKey=''){
  try {
    if(expectedKey){
      const raw=window.sessionStorage?.getItem(STORE_CHECKOUT_ATTEMPT_STORAGE_KEY);
      const saved=raw ? JSON.parse(raw) : null;
      if(saved?.key!==expectedKey || Number(saved.user_id)!==Number(state.userId||0)) return;
    }
    window.sessionStorage?.removeItem(STORE_CHECKOUT_ATTEMPT_STORAGE_KEY);
  } catch(_error) { /* storage is optional */ }
}
function getStoreCheckoutIdempotencyKey(body){
  const userId=Number(state.userId||0);
  const intent=storeCheckoutAttemptIntent(body);
  try {
    const raw=window.sessionStorage?.getItem(STORE_CHECKOUT_ATTEMPT_STORAGE_KEY);
    const saved=raw ? JSON.parse(raw) : null;
    const age=Date.now()-Number(saved?.created_at_ms||0);
    if(saved && Number(saved.user_id)===userId && saved.intent===intent && typeof saved.key==='string' && saved.key.length>=8 && age>=0 && age<STORE_CHECKOUT_ATTEMPT_MAX_AGE_MS){
      return saved.key;
    }
    const key=newStoreCheckoutIdempotencyKey();
    window.sessionStorage?.setItem(STORE_CHECKOUT_ATTEMPT_STORAGE_KEY, JSON.stringify({key,user_id:userId,intent,created_at_ms:Date.now()}));
    return key;
  } catch(_error) {
    return newStoreCheckoutIdempotencyKey();
  }
}
function moneyRub(v){ return `${(Number(v||0)/100).toLocaleString('ru-RU')} ₽`; }
function sausageCountText(kopecks){
  const value=Number(kopecks||0)/100;
  return `${value.toLocaleString('ru-RU',{maximumFractionDigits:2})} колбасок`;
}
function sausagePromoSettings(){ return (state.store.promoSettings||{}).sausages||{}; }
function storeReviewBonusSettings(){ return state.store.reviewBonusSettings||{}; }
function isSausagePromoActive(){ return Number(sausagePromoSettings().is_active||0)>0; }
function productOldPriceKopecks(p){
  const premiumPercent=Number(p?.premium_discount_percent||0);
  const baseKop=Number(p?.base_price_kopecks||0);
  const discountedKop=Number(p?.price_kopecks||0);
  if(premiumPercent>0 && baseKop>discountedKop) return baseKop;
  const oldKop=Number(p?.old_price_kopecks||0);
  const curKop=Number(p?.price_kopecks||0);
  if(!(oldKop>0) || !(oldKop>curKop)) return 0;
  return oldKop;
}
function buildProductPriceEl(p){
  const wrap=document.createElement('div');
  wrap.className='product-price';
  const premiumPercent=Number(p?.premium_discount_percent||0);
  const oldKop=productOldPriceKopecks(p);
  if(premiumPercent>0 && oldKop>0){
    const old=document.createElement('span');
    old.className='product-price__old';
    old.textContent=p?.base_price_text||moneyRub(oldKop);
    wrap.appendChild(old);
    const badge=document.createElement('span');
    badge.className='product-price__premium-discount';
    badge.textContent=`-${premiumPercent}%`;
    badge.title='Скидка по подписке PREMIUM';
    badge.dataset.tooltip='Скидка по подписке PREMIUM';
    badge.tabIndex=0;
    badge.setAttribute('aria-label', `Скидка ${premiumPercent}% по подписке PREMIUM`);
    wrap.appendChild(badge);
  }
  const current=document.createElement('span');
  current.className='product-price__current';
  current.textContent=p?.price_text||moneyRub(p?.price_kopecks);
  wrap.appendChild(current);
  if(premiumPercent<=0 && oldKop>0){
    const old=document.createElement('span');
    old.className='product-price__old';
    old.textContent=p?.old_price_text||moneyRub(oldKop);
    wrap.appendChild(old);
  }
  return wrap;
}
function productPriceHtml(p){
  const current=escapeHtml(p?.price_text||moneyRub(p?.price_kopecks));
  const oldKop=productOldPriceKopecks(p);
  const premiumPercent=Number(p?.premium_discount_percent||0);
  const old=oldKop>0
    ? `<span class="product-price__old">${escapeHtml(premiumPercent>0?(p?.base_price_text||moneyRub(oldKop)):(p?.old_price_text||moneyRub(oldKop)))}</span>`
    : '';
  const badge=premiumPercent>0?`<span class="product-price__premium-discount" data-tooltip="Скидка по подписке PREMIUM" title="Скидка по подписке PREMIUM" tabindex="0" aria-label="Скидка ${premiumPercent}% по подписке PREMIUM">-${premiumPercent}%</span>`:'';
  return `<div class="product-price">${premiumPercent>0?old:''}${badge}<span class="product-price__current">${current}</span>${premiumPercent>0?'':old}</div>`;
}
function storeProductUrl(productId){
  const id=Number(productId||0);
  const url=new URL(window.location.href);
  url.searchParams.set('product', String(id));
  url.hash='home';
  return url.toString();
}
async function copyTextToClipboard(text){
  const value=String(text||'');
  if(!value) return false;
  if(navigator.clipboard?.writeText && window.isSecureContext){
    await navigator.clipboard.writeText(value);
    return true;
  }
  const textarea=document.createElement('textarea');
  textarea.value=value;
  textarea.setAttribute('readonly','');
  textarea.style.position='fixed';
  textarea.style.left='-9999px';
  textarea.style.top='0';
  document.body.appendChild(textarea);
  textarea.focus();
  textarea.select();
  let copied=false;
  try {
    copied=document.execCommand('copy');
  } finally {
    textarea.remove();
  }
  return copied;
}
async function copyStoreProductLink(item, button){
  const url=storeProductUrl(item?.id);
  const previous=button?.textContent || 'Поделиться';
  if(button){
    button.disabled=true;
    button.textContent='Копируем...';
  }
  try {
    const copied=await copyTextToClipboard(url);
    if(!copied) throw new Error('copy_failed');
    showToast('Ссылка на товар скопирована.');
    if(button) button.textContent='Скопировано';
  } catch (_error) {
    showToast('Не удалось скопировать ссылку. Скопируйте адрес из браузера.', true);
    if(button) button.textContent=previous;
  } finally {
    if(button){
      setTimeout(() => {
        button.disabled=false;
        button.textContent=previous;
      }, 1400);
    }
  }
}
function storeCartStorageKey(){ return 'kolbabot_store_cart_guest'; }
function legacyUserCartStorageKey(userId){ return `kolbabot_store_cart_user_${Number(userId||0)}`; }
function loadLegacyUserCartLocal(userId){
  const key = legacyUserCartStorageKey(userId);
  if(!Number(userId||0)) return [];
  try {
    const items = sanitizeStoreCart(JSON.parse(localStorage.getItem(key)||'[]'));
    localStorage.removeItem(key);
    return items;
  } catch(_e){
    localStorage.removeItem(key);
    return [];
  }
}
function normalizeCartQuantity(value, { allowZero = true } = {}){ const parsed=Number.parseInt(String(value??'').replace(/[^0-9]/g,''),10); if(!Number.isFinite(parsed)) return allowZero ? 0 : 1; const min=allowZero ? 0 : 1; return Math.max(min, Math.min(999, parsed)); }
function catalogProductById(productId){ return (state.store.products||[]).find((p)=>Number(p.id)===Number(productId)); }
function resolveCartItemShippingWeightGrams(item){
  const fromCatalog=catalogProductById(item?.product_id||item?.id);
  if(fromCatalog){
    const grams=Number(fromCatalog.shipping_weight_grams||fromCatalog.weight_grams||0);
    if(Number.isFinite(grams) && grams>0) return grams;
  }
  const fromItem=Number(item?.shipping_weight_grams||item?.weight_grams||0);
  if(Number.isFinite(fromItem) && fromItem>0) return fromItem;
  const kg=Number(item?.weight_kg||0);
  if(Number.isFinite(kg) && kg>0) return Math.round(kg*1000);
  return 0;
}
function sanitizeStoreCart(raw){
  if(!Array.isArray(raw)) return [];
  const seen=new Set();
  return raw.filter((item)=>{
    const productId=Number(item?.product_id||item?.id||0);
    return productId>0 && String(item?.source_id||'')!=='nitrite_cashback';
  }).map((item)=>{
    const product_id=Number(item?.product_id||item?.id||0);
    const catalog=catalogProductById(product_id);
    const weight_grams=resolveCartItemShippingWeightGrams(item);
    return {
      product_id,
      title:catalog&&storeVariantGroup(catalog)?storeProductCartTitle(catalog):String(item?.title||catalog?.title||'Товар'),
      base_price_kopecks:Number(catalog?.base_price_kopecks??item?.base_price_kopecks??catalog?.price_kopecks??item?.price_kopecks??0),
      base_price_text:String(catalog?.base_price_text||item?.base_price_text||''),
      price_kopecks:Number(catalog?.price_kopecks??item?.price_kopecks??0),
      price_text:String(catalog?.price_text||item?.price_text||''),
      premium_discount_percent:Number(catalog?.premium_discount_percent??item?.premium_discount_percent??0),
      photo_url:item?.photo_url||catalog?.photo_url||'',
      weight:String(weight_grams||item?.weight||catalog?.weight||''),
      weight_kg:weight_grams/1000,
      weight_grams,
      quantity:normalizeCartQuantity(item?.quantity, { allowZero:false }),
    };
  }).filter((item)=>{ const key=String(item.product_id); if(item.product_id<=0 || seen.has(key)) return false; seen.add(key); return item.quantity>0; });
}
function loadStoreCartLocal(){ try { return sanitizeStoreCart(JSON.parse(localStorage.getItem(storeCartStorageKey())||'[]')); } catch(_e){ return []; } }
function saveStoreCartLocal(cart){ localStorage.setItem(storeCartStorageKey(), JSON.stringify(sanitizeStoreCart(cart||[]))); }
function clearStoreCartLocal(){ localStorage.removeItem(storeCartStorageKey()); }
function mergeStoreCarts(primary, secondary){
  const map = new Map();
  [...sanitizeStoreCart(primary), ...sanitizeStoreCart(secondary)].forEach((item)=>{
    const prev = map.get(item.product_id);
    if(!prev){ map.set(item.product_id, { ...item }); return; }
    prev.quantity = Math.min(999, Number(prev.quantity||0) + Number(item.quantity||0));
  });
  return Array.from(map.values());
}
async function fetchStoreCartFromDb(){
  const data = await api('/api/store/cart');
  return sanitizeStoreCart(data.items||[]);
}
async function persistStoreCartToDb(cart){
  const data = await api('/api/store/cart', {
    method: 'PUT',
    body: { items: sanitizeStoreCart(cart||[]).map((item)=>({ product_id: item.product_id, quantity: item.quantity })) },
  });
  return sanitizeStoreCart(data.items||[]);
}
function scheduleStoreCartPersist(){
  if(!(state.isAuthenticated && Number(state.userId||0)>0)) return;
  const seq = ++storeCartSaveSeq;
  if(storeCartSaveTimer) clearTimeout(storeCartSaveTimer);
  storeCartSaveTimer = setTimeout(async ()=>{
    storeCartSaveTimer = null;
    try {
      const saved = await persistStoreCartToDb(state.store.cart||[]);
      if(seq === storeCartSaveSeq) state.store.cart = sanitizeStoreCart(saved);
    } catch(error){
      showToast(errorText(error, 'Не удалось сохранить корзину.'), true);
    }
  }, 250);
}
function loadStoreCart(){ return loadStoreCartLocal(); }
function reloadStoreCart(){ state.store.cart = loadStoreCartLocal(); }
function saveStoreCart(){
  const cart = sanitizeStoreCart(state.store.cart||[]);
  state.store.cart = cart;
  if(state.isAuthenticated && Number(state.userId||0)>0){
    scheduleStoreCartPersist();
    return;
  }
  saveStoreCartLocal(cart);
}
function clearStoreCart(){
  state.store.cart=[];
  if(state.isAuthenticated && Number(state.userId||0)>0){
    storeCartSaveSeq += 1;
    if(storeCartSaveTimer){ clearTimeout(storeCartSaveTimer); storeCartSaveTimer=null; }
    void persistStoreCartToDb([]).catch(()=>{});
  } else {
    clearStoreCartLocal();
  }
  renderStore();
}
async function syncStoreCartFromServer(){
  if(!(state.isAuthenticated && Number(state.userId||0)>0)){
    reloadStoreCart();
    renderStore();
    return;
  }
  const guestCart = loadStoreCartLocal();
  const legacyUserCart = loadLegacyUserCartLocal(state.userId);
  try {
    let serverCart = await fetchStoreCartFromDb();
    const localToMerge = mergeStoreCarts(guestCart, legacyUserCart);
    if(localToMerge.length){
      const merged = mergeStoreCarts(serverCart, localToMerge);
      serverCart = sanitizeStoreCart(await persistStoreCartToDb(merged));
      clearStoreCartLocal();
    }
    state.store.cart = serverCart;
  } catch(error){
    // Fallback: keep local guest cart until API is available.
    state.store.cart = mergeStoreCarts(guestCart, legacyUserCart);
    showToast(errorText(error, 'Не удалось загрузить корзину с сервера.'), true);
  }
  renderStore();
}
function cartCount(){ return (state.store.cart||[]).reduce((s,i)=>s+Number(i.quantity||0),0); }
function cartProductsTotal(){
  return (state.store.cart||[]).reduce((sum,item)=>{
    return sum+Math.max(0, Number(item.price_kopecks||0)*Number(item.quantity||1));
  },0);
}
function storeCashbackPercent(){
  const promo=sausagePromoSettings();
  return Math.max(0, Number(state.store.paidPremiumCashback?promo.premium_percent:promo.standard_percent)||0);
}
function cartSausageRedemptionLimit(){
  if(!isSausagePromoActive()) return 0;
  const balance=Math.max(0, Number((state.store.rewards||{}).sausages_kopecks||0));
  const maxPercent=Math.max(0, Math.min(50, Number(sausagePromoSettings().max_redemption_percent||50)));
  return Math.max(0, Math.min(balance, Math.floor(cartProductsTotal()*maxPercent/100)));
}
function cartSausageSpent(){
  return state.store.useSausages ? cartSausageRedemptionLimit() : 0;
}
function cartSausageBonus(){
  if(!isSausagePromoActive()) return 0;
  const percent=storeCashbackPercent();
  return Math.max(0, Math.floor(cartSubtotal()*percent/100));
}
function renderCartTotal(totalEl, totalKopecks){
  if(!totalEl) return;
  const spent=cartSausageSpent();
  const totalText=`Итого с доставкой: ${moneyRub(totalKopecks)}${spent?` (колбасками списано ${sausageCountText(spent)})`:''}`;
  const bonusLine=isSausagePromoActive()
    ? `<div class="cart-total__bonus">За этот заказ Вам будет начислено ${escapeHtml(sausageCountText(cartSausageBonus()))}</div>`
    : '';
  const missing=cartMinimumOrderMissingKopecks();
  const minimumLine=missing>0
    ? `<div class="cart-total__minimum">Для оформления заказа добавьте товары ещё на ${escapeHtml(Math.ceil(missing/100).toLocaleString('ru-RU'))} рублей</div>`
    : '';
  setHtml(totalEl, `<div>${escapeHtml(totalText)}</div>${bonusLine}${minimumLine}`);
}
function reconcileSausageRedemption(){
  if(!isSausagePromoActive() || !cartCount() || cartSausageRedemptionLimit()<=0){
    state.store.useSausages=false;
  }
}
function cartSubtotal(){ return Math.max(0, cartProductsTotal()-cartSausageSpent()); }
function cartWeightGrams(){ return (state.store.cart||[]).reduce((s,i)=>s+resolveCartItemShippingWeightGrams(i)*Number(i.quantity||1),0); }
function getCartRow(productId){ return (state.store.cart||[]).find(i=>Number(i.product_id)===Number(productId)); }
function pickStoreDeliverySizeForWeight(weightGrams, sizes){
  const ordered=[...(sizes||[])].filter((s)=>Number(s.max_weight_grams||0)>0)
    .sort((a,b)=>Number(a.max_weight_grams||0)-Number(b.max_weight_grams||0)||Number(a.sort_order||0)-Number(b.sort_order||0));
  if(!ordered.length) return null;
  const w=Math.max(0, Number(weightGrams||0));
  return ordered.find((s)=>w<=Number(s.max_weight_grams||0)) || ordered[ordered.length-1];
}
function calculateStoreDeliveryQuote(totalWeightGrams){
  const cart=state.store.cart||[];
  if(cart.length && cart.every(isStoreCartRowDigital)) return { total_weight_grams:0, parcels_count:0, price_kopecks:0, parcels:[], is_free_digital:true };
  // Свыше 15 кг: XL за первые 15 кг + остаток по сетке (не второй полный XL).
  const sizes=state.store.deliverySizes||[];
  const maxParcel=Math.max(0, ...sizes.map((s)=>Number(s.max_weight_grams||0)), 15000);
  let remaining=Math.max(0, Number(totalWeightGrams||0));
  if(remaining<=0 && cartCount()>0) remaining=1;
  const parcels=[];
  let price=0;
  while(remaining>0){
    const chunk=Math.min(remaining, maxParcel||15000);
    const size=pickStoreDeliverySizeForWeight(chunk, sizes);
    if(!size) break;
    const sizePrice=Math.max(0, Number(size.price_kopecks||0));
    parcels.push({ code:size.code, title:size.title, weight_grams:chunk, price_kopecks:sizePrice });
    price+=sizePrice;
    remaining-=chunk;
  }
  return { total_weight_grams:Math.max(0, Number(totalWeightGrams||0)), parcels_count:parcels.length, price_kopecks:price, parcels };
}
function isStoreProductDigital(product){
  if(product?.product_type) return product.product_type==='digital_book';
  return String(product?.category_name||product?.category_title||'').trim().toLowerCase()==='цифровые товары'
    || ['digital','digital-products','digital_goods'].includes(String(product?.category_id||product?.category_external_id||'').trim().toLowerCase());
}
function isStoreCartRowDigital(row){
  return isStoreProductDigital(catalogProductById(row?.product_id)||row);
}
function cartHasDigitalProducts(){
  return (state.store.cart||[]).some(isStoreCartRowDigital);
}
function cartOnlyDigitalProducts(){
  const cart=state.store.cart||[];
  return cart.length>0 && cart.every(isStoreCartRowDigital);
}
function selectedStoreDeliveryCode(){
  return String(document.getElementById('deliveryMethodSelect')?.value||'').trim();
}
function ozonDeliveryRequired(){
  return selectedStoreDeliveryCode()==='ozon' && cartCount()>0 && !cartOnlyDigitalProducts();
}
function ozonQuoteRequired(){
  return ozonDeliveryRequired() && Boolean(state.store.ozonDelivery?.checkoutEnabled) && !state.store.ozonDelivery?.manualPoint;
}
function currentOzonQuoteFingerprint(){
  const pointId=Number((state.store.ozonDelivery||{}).selectedPoint?.delivery_point_id||0);
  const phone=String(document.querySelector('#checkoutForm [name="phone"]')?.value||'').replace(/\D+/g,'');
  const items=[...(state.store.cart||[])]
    .map((item)=>{
      const product=catalogProductById(item?.product_id)||item||{};
      return [
        Number(item?.product_id||0),
        Number(item?.quantity||0),
        Number(product?.price_kopecks||0),
        Number(product?.weight_grams||product?.shipping_weight_grams||0),
        String(product?.dimensions||''),
      ].join(':');
    })
    .sort()
    .join('|');
  return `${pointId}|${phone}|${state.store.premiumActive?'premium':'standard'}|${items}`;
}
function currentOzonQuote(){
  const ozon=state.store.ozonDelivery||{};
  if(!ozon.quote || ozon.quoteFingerprint!==currentOzonQuoteFingerprint()) return null;
  return ozon.quote;
}
function currentOzonDeliveryEstimate(){
  const ozon=state.store.ozonDelivery||{};
  const pointId=Number(ozon.selectedPoint?.delivery_point_id||0);
  if(!ozon.estimate || Number(ozon.estimatePointId||0)!==pointId) return null;
  return ozon.estimate;
}
function ozonQuoteConfirmationPayload(quote){
  const point=quote?.delivery_point||{};
  return {
    price_kopecks:Number(quote?.price_kopecks||0),
    delivery_cost_kopecks:Number(quote?.delivery_cost_kopecks||0),
    insurance_cost_kopecks:Number(quote?.insurance_cost_kopecks||0),
    estimated_delivery_days:Number(quote?.estimated_delivery_days||0),
    delivery_point:{
      delivery_point_id:Number(point.delivery_point_id||0),
      full_address:String(point.full_address||'').trim(),
    },
  };
}
function invalidateOzonDeliveryQuote({ keepPoint=true }={}){
  const ozon=state.store.ozonDelivery||{};
  ozon.quote=null;
  ozon.quoteFingerprint='';
  if(!keepPoint){
    ozon.selectedPoint=null;
    ozon.manualPoint=null;
    ozon.estimate=null;
    ozon.estimatePointId=0;
    ozon.estimateSeq=Number(ozon.estimateSeq||0)+1;
  }
  state.store.ozonDelivery=ozon;
}
function invalidateOzonDeliveryEstimate(){
  const ozon=state.store.ozonDelivery||{};
  ozon.estimate=null;
  ozon.estimatePointId=0;
  ozon.estimateSeq=Number(ozon.estimateSeq||0)+1;
  state.store.ozonDelivery=ozon;
}
function ozonDeliveryDaysText(days){
  const value=Math.max(0,Number(days||0));
  if(!value) return '';
  const mod10=value%10;
  const mod100=value%100;
  const suffix=mod10===1&&mod100!==11?'день':(mod10>=2&&mod10<=4&&(mod100<12||mod100>14)?'дня':'дней');
  return `${value} ${suffix}`;
}
function ozonDeliveryQuoteText(quote){
  if(!quote) return '';
  const total=quote.price_text||moneyRub(quote.price_kopecks||0);
  const eta=quote.estimated_delivery_days?` · Примерный срок доставки: ${ozonDeliveryDaysText(quote.estimated_delivery_days)}`:'';
  const insurance=Number(quote.insurance_cost_kopecks||0)>0
    ? ` · включая страховку ${quote.insurance_cost_text||moneyRub(quote.insurance_cost_kopecks)}`
    : '';
  return `${total}${eta}${insurance}`;
}
function ozonDeliveryEstimateText(estimate){
  const days=Number(estimate?.estimated_delivery_days||0);
  return days>0?`Примерный срок доставки: ${ozonDeliveryDaysText(days)}`:'';
}
function updateOzonDeliveryUi(){
  const ozon=state.store.ozonDelivery||{};
  const required=ozonDeliveryRequired();
  const digitalOnly=cartOnlyDigitalProducts();
  const field=document.getElementById('ozonPvzField');
  const deliveryMethodField=document.getElementById('checkoutDeliveryMethodField');
  const deliveryMethodSelect=document.getElementById('deliveryMethodSelect');
  const addressField=document.getElementById('checkoutAddressField');
  const addressInput=addressField?.querySelector('[name="address"]');
  const pointInput=document.getElementById('ozonDeliveryPointId');
  const manualAddressInput=document.getElementById('ozonManualPvzAddressValue');
  const manualNameInput=document.getElementById('ozonManualPvzNameValue');
  const button=document.getElementById('ozonPvzOpenBtn');
  const selection=document.getElementById('ozonPvzSelection');
  if(field){ field.hidden=!required; field.classList.toggle('hidden',!required); }
  if(deliveryMethodField){ deliveryMethodField.hidden=digitalOnly; deliveryMethodField.classList.toggle('hidden',digitalOnly); }
  if(deliveryMethodSelect){ deliveryMethodSelect.disabled=digitalOnly; deliveryMethodSelect.required=!digitalOnly; }
  const hideAddress=digitalOnly || selectedStoreDeliveryCode()==='ozon';
  if(addressField){ addressField.hidden=hideAddress; addressField.classList.toggle('hidden',hideAddress); }
  if(addressInput) addressInput.required=!hideAddress;
  const manual=ozon.manualPoint||null;
  if(pointInput) pointInput.value=required&&!manual?String(ozon.selectedPoint?.delivery_point_id||''):'';
  if(manualAddressInput) manualAddressInput.value=required&&manual?String(manual.address||''):'';
  if(manualNameInput) manualNameInput.value=required&&manual?String(manual.name||''):'';
  if(button){
    button.disabled=!ozon.enabled;
    button.textContent=(ozon.selectedPoint||manual)
      ? 'Выбрать другой ПВЗ'
      : !ozon.enabled
        ? 'Ozon Delivery не настроен'
        : 'Выбрать ПВЗ';
  }
  if(!selection) return;
  if(!required){ selection.textContent=''; return; }
  if(manual){
    selection.className='ozon-pvz-selection';
    setHtml(selection,`<strong>${escapeHtml(manual.name||'ПВЗ Ozon (введён вручную)')}</strong><span>${escapeHtml(manual.address||'')}</span><span class="muted">ПВЗ будет обработан вручную. Срок доставки Ozon для этой точки не рассчитывается.</span>`);
    return;
  }
  const point=ozon.selectedPoint;
  if(!point){
    selection.className='ozon-pvz-selection muted';
    selection.textContent=ozon.enabled?'Пункт выдачи не выбран.':'Ozon Delivery временно недоступен.';
    return;
  }
  const quote=currentOzonQuote();
  const estimate=currentOzonDeliveryEstimate();
  const quoteText=ozon.checkoutEnabled
    ? quote
      ? `<span class="ozon-pvz-selection__quote">${escapeHtml(ozonDeliveryQuoteText(quote))}</span>`
      : '<span class="muted">Стоимость и срок ещё не рассчитаны.</span>'
    : estimate
      ? `<span class="ozon-pvz-selection__quote">${escapeHtml(ozonDeliveryEstimateText(estimate))}</span>`
      : '<span class="muted">Примерный срок доставки рассчитывается.</span>';
  selection.className='ozon-pvz-selection';
  setHtml(selection,`<strong>${escapeHtml(point.name||'ПВЗ Ozon')}</strong><span>${escapeHtml(point.full_address||'')}</span>${quoteText}`);
}
function openOzonPvzDialog(){
  if(!(state.isAuthenticated && Number(state.userId||0)>0)){ openPaywall('auth_required'); return; }
  if(!state.store.ozonDelivery?.enabled){ showToast('Ozon Delivery пока не настроен.',true); return; }
  const dialog=document.getElementById('ozonPvzDialog');
  if(typeof dialog?.showModal==='function' && !dialog.open) dialog.showModal();
  window.setTimeout(()=>document.getElementById('ozonPvzSearchInput')?.focus(),0);
}
function closeOzonPvzDialog(){ const ozon=state.store.ozonDelivery||(state.store.ozonDelivery={}); ozon.searchController?.abort(); window.clearTimeout(ozon.searchTimer); ozon.searchTimer=0; ozon.searchSeq=Number(ozon.searchSeq||0)+1; const manualForm=document.getElementById('ozonPvzManualForm'); if(manualForm) manualForm.hidden=true; document.getElementById('ozonPvzDialog')?.close(); }
function setOzonManualPvzFormVisible(visible){
  const form=document.getElementById('ozonPvzManualForm');
  if(!form) return;
  form.hidden=!visible;
  if(!visible) return;
  const ozon=state.store.ozonDelivery||{};
  const address=document.getElementById('ozonPvzManualAddress');
  const name=document.getElementById('ozonPvzManualName');
  if(address) address.value=String(ozon.manualPoint?.address||'');
  if(name) name.value=String(ozon.manualPoint?.name||'');
  window.setTimeout(()=>address?.focus(),0);
}
function selectManualOzonPvz(){
  const address=String(document.getElementById('ozonPvzManualAddress')?.value||'').trim().replace(/\s+/g,' ');
  const name=String(document.getElementById('ozonPvzManualName')?.value||'').trim().replace(/\s+/g,' ');
  const status=document.getElementById('ozonPvzSearchStatus');
  if(address.length<5){ if(status) status.textContent='Укажите полный адрес ПВЗ Ozon.'; return false; }
  if(address.length>500 || name.length>200){ if(status) status.textContent='Адрес или название ПВЗ слишком длинные.'; return false; }
  const ozon=state.store.ozonDelivery||(state.store.ozonDelivery={});
  ozon.selectedPoint=null;
  ozon.manualPoint={ address, name };
  ozon.quote=null;
  ozon.quoteFingerprint='';
  invalidateOzonDeliveryEstimate();
  closeOzonPvzDialog();
  renderCart();
  return true;
}
document.getElementById('ozonPvzManualOpenBtn')?.addEventListener('click',()=>setOzonManualPvzFormVisible(true));
document.getElementById('ozonPvzManualCancelBtn')?.addEventListener('click',()=>setOzonManualPvzFormVisible(false));
document.getElementById('ozonPvzManualForm')?.addEventListener('submit',(event)=>{ event.preventDefault(); selectManualOzonPvz(); });
function renderOzonPvzResults(items){
  const wrap=document.getElementById('ozonPvzResults');
  if(!wrap) return;
  setHtml(wrap,'');
  (items||[]).forEach((point)=>{
    const button=document.createElement('button');
    button.type='button';
    button.className='ozon-pvz-result';
    const type=String(point.type||'')==='postamat'?'Постамат':'ПВЗ';
    const storage=Number(point.storage_period_days||0)>0?` · хранение ${Number(point.storage_period_days)} дн.`:'';
    button.innerHTML=`<strong>${escapeHtml(point.name||'Пункт Ozon')}</strong><span>${escapeHtml(point.full_address||'')}</span><small>${escapeHtml(type+storage)}</small>`;
    button.addEventListener('click',()=>selectOzonDeliveryPoint(point));
    wrap.appendChild(button);
  });
}
function scheduleOzonPvzSearch(){
  const input=document.getElementById('ozonPvzSearchInput');
  const status=document.getElementById('ozonPvzSearchStatus');
  const results=document.getElementById('ozonPvzResults');
  const query=String(input?.value||'').trim();
  const ozon=state.store.ozonDelivery||(state.store.ozonDelivery={});
  ozon.searchController?.abort();
  window.clearTimeout(ozon.searchTimer);
  ozon.searchTimer=0;
  ozon.searchSeq=Number(ozon.searchSeq||0)+1;
  results?.removeAttribute('aria-busy');
  if(query.length<2){
    renderOzonPvzResults([]);
    if(status) status.textContent='Введите минимум два символа.';
    return;
  }
  if(status) status.textContent='Ищем по мере ввода…';
  ozon.searchTimer=window.setTimeout(()=>searchOzonPvz(),280);
}
async function searchOzonPvz(ev){
  ev?.preventDefault();
  const input=document.getElementById('ozonPvzSearchInput');
  const status=document.getElementById('ozonPvzSearchStatus');
  const query=String(input?.value||'').trim();
  if(query.length<2){ const ozon=state.store.ozonDelivery||(state.store.ozonDelivery={}); ozon.searchSeq=Number(ozon.searchSeq||0)+1; renderOzonPvzResults([]); document.getElementById('ozonPvzResults')?.removeAttribute('aria-busy'); if(status) status.textContent='Введите минимум два символа.'; return; }
  const ozon=state.store.ozonDelivery||(state.store.ozonDelivery={});
  ozon.searchController?.abort();
  window.clearTimeout(ozon.searchTimer);
  ozon.searchTimer=0;
  const seq=Number(ozon.searchSeq||0)+1;
  ozon.searchSeq=seq;
  ozon.searchController=new AbortController();
  if(status) status.textContent='Ищем пункты выдачи…';
  const results=document.getElementById('ozonPvzResults');
  results?.setAttribute('aria-busy','true');
  try{
    const data=await api(`/api/store/ozon-delivery/points?q=${encodeURIComponent(query)}&limit=40`, {signal:ozon.searchController.signal,retry:false,timeoutMs:8000});
    if(seq!==Number((state.store.ozonDelivery||{}).searchSeq||0)) return;
    const items=Array.isArray(data.items)?data.items:[];
    renderOzonPvzResults(items);
    results?.removeAttribute('aria-busy');
    if(status) status.textContent=items.length?`Найдено: ${items.length}`:'Ничего не найдено. Уточните город или улицу.';
  }catch(error){
    if(seq!==Number((state.store.ozonDelivery||{}).searchSeq||0)) return;
    results?.removeAttribute('aria-busy');
    if(status) status.textContent=errorText(error,'Не удалось загрузить пункты Ozon.');
  }
}
async function selectOzonDeliveryPoint(point){
  const ozon=state.store.ozonDelivery||{};
  if(String(point?.checkout_mode||'')==='manual'){
    const number=String(point?.delivery_point_number||'').trim();
    const baseName=String(point?.name||'ПВЗ Ozon').trim();
    ozon.selectedPoint=null;
    ozon.manualPoint={
      address:String(point?.full_address||'').trim(),
      name:number&&!baseName.includes(number)?`${baseName} ${number}`:baseName,
    };
  }else{
    ozon.selectedPoint=point;
    ozon.manualPoint=null;
  }
  ozon.quote=null;
  ozon.quoteFingerprint='';
  invalidateOzonDeliveryEstimate();
  state.store.ozonDelivery=ozon;
  closeOzonPvzDialog();
  renderCart();
  if(ozonQuoteRequired()) await refreshOzonDeliveryQuote();
  else if(ozonDeliveryRequired() && ozon.selectedPoint) await refreshOzonDeliveryEstimate({quiet:true});
}
async function refreshOzonDeliveryEstimate({ quiet=false }={}){
  const ozon=state.store.ozonDelivery||{};
  const pointId=Number(ozon.selectedPoint?.delivery_point_id||0);
  if(!ozonDeliveryRequired() || ozon.checkoutEnabled || ozon.manualPoint || !pointId) return true;
  const seq=Number(ozon.estimateSeq||0)+1;
  ozon.estimateSeq=seq;
  ozon.estimate=null;
  ozon.estimatePointId=0;
  updateOzonDeliveryUi();
  try{
    const data=await api('/api/store/ozon-delivery/estimate',{
      method:'POST',
      timeoutMs:15000,
      body:{delivery_point_id:pointId},
    });
    const current=state.store.ozonDelivery||{};
    if(seq!==Number(current.estimateSeq||0) || pointId!==Number(current.selectedPoint?.delivery_point_id||0)) return false;
    ozon.estimate=data.estimate||null;
    ozon.estimatePointId=pointId;
    renderCart();
    return Boolean(ozon.estimate);
  }catch(error){
    if(seq===Number((state.store.ozonDelivery||{}).estimateSeq||0)){
      ozon.estimate=null;
      ozon.estimatePointId=0;
      renderCart();
    }
    if(!quiet) showToast(errorText(error,'Не удалось рассчитать срок доставки Ozon.'),true);
    return false;
  }
}
async function refreshOzonDeliveryQuote({ quiet=false }={}){
  if(!ozonQuoteRequired()) return true;
  const ozon=state.store.ozonDelivery||{};
  const pointId=Number(ozon.selectedPoint?.delivery_point_id||0);
  if(!pointId){ if(!quiet) showToast('Сначала выберите пункт выдачи Ozon.',true); return false; }
  const phone=String(document.querySelector('#checkoutForm [name="phone"]')?.value||'').trim();
  if(phone.replace(/\D+/g,'').length<10){ if(!quiet) showToast('Укажите телефон перед расчётом доставки Ozon.',true); return false; }
  const fingerprint=currentOzonQuoteFingerprint();
  const seq=Number(ozon.quoteSeq||0)+1;
  ozon.quoteSeq=seq;
  ozon.quote=null;
  ozon.quoteFingerprint='';
  const deliveryEl=document.getElementById('cartDeliveryPrice');
  if(deliveryEl) deliveryEl.textContent='Ozon рассчитывает стоимость и срок…';
  updateOzonDeliveryUi();
  updateCheckoutAuthUi();
  try{
    const data=await api('/api/store/ozon-delivery/quote',{
      method:'POST',
      timeoutMs:60000,
      body:{
        delivery_point_id:pointId,
        phone,
        items:(state.store.cart||[]).map((item)=>({product_id:item.product_id,quantity:item.quantity})),
      },
    });
    if(seq!==Number((state.store.ozonDelivery||{}).quoteSeq||0) || fingerprint!==currentOzonQuoteFingerprint()) return false;
    ozon.quote=data.quote||null;
    ozon.quoteFingerprint=fingerprint;
    if(data.quote?.delivery_point) ozon.selectedPoint=data.quote.delivery_point;
    renderCart();
    return Boolean(ozon.quote);
  }catch(error){
    if(seq===Number((state.store.ozonDelivery||{}).quoteSeq||0)){
      ozon.quote=null;
      ozon.quoteFingerprint='';
      renderCart();
    }
    if(!quiet) showToast(errorText(error,'Не удалось рассчитать Ozon Delivery.'),true);
    return false;
  }
}
function updateDigitalDeliveryEmailField(){
  const field=document.getElementById('digitalDeliveryEmailField');
  const input=field?.querySelector('input');
  if(!field||!input) return;
  const visible=cartHasDigitalProducts();
  field.hidden=!visible;
  field.classList.toggle('hidden',!visible);
  input.required=visible;
  const nameHint=document.getElementById('checkoutBookNameHint');
  if(nameHint){ nameHint.hidden=!visible; nameHint.classList.toggle('hidden',!visible); }
}
function storeMinimumOrderKopecks(){ return Math.max(0, Number((state.store.storeSettings||state.store.settings||{}).minimum_order_kopecks || (state.admin.storeSettings||{}).minimum_order_kopecks || 0)); }
function cartMinimumOrderMissingKopecks(){ return cartOnlyDigitalProducts() ? 0 : Math.max(0, storeMinimumOrderKopecks()-cartSubtotal()); }

function renderCartMinimumCounter(highlightError = false) {
  const elCounter = document.getElementById('cartMinimumCounter');
  if (!elCounter) return;
  const minKopecks = storeMinimumOrderKopecks();
  if (minKopecks <= 0 || cartOnlyDigitalProducts()) {
    elCounter.classList.add('hidden');
    elCounter.classList.remove('is-error');
    setHtml(elCounter, '');
    return;
  }
  elCounter.classList.remove('hidden');
  const subtotalKopecks = cartSubtotal();
  const missingKopecks = cartMinimumOrderMissingKopecks();

  let hintText = '';
  if (missingKopecks > 0) {
    hintText = ` (не хватает ${moneyRub(missingKopecks)})`;
  } else {
    hintText = ` (набрана)`;
  }

  const labelHtml = `<span class="cart-minimum-counter__label">Минимальная сумма заказа: </span>`;
  const valueHtml = `<strong class="cart-minimum-counter__value">${moneyRub(subtotalKopecks)} / ${moneyRub(minKopecks)}</strong>`;
  const hintHtml = `<span class="cart-minimum-counter__hint">${escapeHtml(hintText)}</span>`;

  setHtml(elCounter, `${labelHtml}${valueHtml}${hintHtml}`);

  if (highlightError && missingKopecks > 0) {
    elCounter.classList.add('is-error');
  } else if (missingKopecks <= 0) {
    elCounter.classList.remove('is-error');
  }
}

function updateCheckoutAuthUi(){
  const submit=document.getElementById('checkoutSubmitBtn');
  if(!submit) return;
  const loggedIn=Boolean(state.isAuthenticated && Number(state.userId||0)>0);
  submit.disabled=!cartCount();
  if(loggedIn){
    submit.type='submit';
    submit.textContent='Оформить заказ';
    submit.onclick=null;
  } else {
    submit.type='button';
    submit.textContent='Войти, чтобы оплатить';
    submit.onclick=()=>openPaywall('auth_required');
  }
}
function setCartQuantity(productId, quantity){
  const cart=state.store.cart||[];
  const idx=cart.findIndex(i=>Number(i.product_id)===Number(productId));
  if(idx<0) return;
  const next=normalizeCartQuantity(quantity);
  if(next<=0){
    cart.splice(idx,1);
  }
  else {
    const row=cart[idx];
    const grams=resolveCartItemShippingWeightGrams(row);
    row.quantity=next;
    row.weight_grams=grams;
    row.shipping_weight_grams=grams;
    row.weight_kg=grams/1000;
    row.weight=String(grams||row.weight||'');
  }
  state.store.cart=cart;
  reconcileSausageRedemption();
  saveStoreCart();
  renderStore();
}
function buildQuantityControl(product, compact=false){
  const row=getCartRow(product.id);
  const qty=normalizeCartQuantity(row?.quantity);
  const wrap=document.createElement('div');
  wrap.className=`quantity-stepper${compact?' quantity-stepper--compact':''}`;
  wrap.setAttribute('aria-label', `Количество: ${product.title||'товар'}`);
  const minus=document.createElement('button');
  minus.type='button';
  minus.className='quantity-stepper__btn';
  minus.textContent='−';
  minus.setAttribute('aria-label','Уменьшить количество');
  minus.onclick=(e)=>{e.stopPropagation(); setCartQuantity(product.id, qty-1);};
  const val=document.createElement('input');
  val.type='text';
  val.inputMode='numeric';
  val.pattern='[0-9]*';
  val.maxLength=3;
  val.className='quantity-stepper__value';
  val.value=String(qty);
  val.name=`cart_qty_${Number(product.id||0)}`;
  val.setAttribute('autocomplete','off');
  val.setAttribute('autocapitalize','off');
  val.setAttribute('autocorrect','off');
  val.setAttribute('spellcheck','false');
  val.setAttribute('data-lpignore','true');
  val.setAttribute('data-1p-ignore','true');
  val.setAttribute('data-form-type','other');
  val.setAttribute('aria-label','Количество товара');
  val.onclick=(e)=>e.stopPropagation();
  val.onkeydown=(e)=>e.stopPropagation();
  val.onchange=(e)=>{e.stopPropagation(); setCartQuantity(product.id, val.value);};
  val.onblur=(e)=>{e.stopPropagation(); setCartQuantity(product.id, val.value);};
  const plus=document.createElement('button');
  plus.type='button';
  plus.className='quantity-stepper__btn';
  plus.textContent='+';
  plus.setAttribute('aria-label','Увеличить количество');
  plus.onclick=(e)=>{e.stopPropagation(); addToCart(product, { silent: true });};
  wrap.append(minus,val,plus);
  return wrap;
}
function buildStoreAction(product){ const row=getCartRow(product.id); if(row) return buildQuantityControl(product); const btn=document.createElement('button'); btn.type='button'; btn.className='chip store-add-btn'; btn.innerHTML='<strong>В корзину</strong><span>Добавить</span>'; btn.onclick=(e)=>{e.stopPropagation(); addToCart(product);}; return btn; }
function storeVariantGroup(product){
  return isStoreProductDigital(product) ? '' : String(product?.variant_group||'').trim();
}
function storeVariantLabel(product){
  return String(product?.variant_label||'').trim() || String(product?.title||'Товар').trim();
}
function storeProductCartTitle(product){
  const title=String(product?.title||'Товар').trim();
  const label=String(product?.variant_label||'').trim();
  const normalize=value=>value.toLocaleLowerCase('ru-RU').replace(/\s+/g,'');
  return storeVariantGroup(product)&&label&&!normalize(title).includes(normalize(label)) ? `${title} · ${label}` : title;
}
function getStoreProductVariants(product, detail=null){
  const group=storeVariantGroup(product);
  if(!group) return [];
  const source=Array.isArray(detail?.variants) ? detail.variants
    : (Array.isArray(product?.variants) ? product.variants : (state.store.products||[]));
  const seen=new Set();
  return [product,...source].filter(candidate=>{
    const id=Number(candidate?.id||0);
    if(!id || seen.has(id) || storeVariantGroup(candidate)!==group || Number(candidate.is_active??1)!==1) return false;
    seen.add(id);
    return true;
  }).sort((a,b)=>storeVariantLabel(a).localeCompare(storeVariantLabel(b),'ru',{numeric:true})||Number(a.id)-Number(b.id));
}
function groupStoreCatalogProducts(products, preferFirst=false){
  const groups=new Map();
  (products||[]).forEach(product=>{
    const group=storeVariantGroup(product);
    const key=group ? `group:${group}` : `sku:${product.id}`;
    const previous=groups.get(key);
    if(!previous || (!preferFirst && Number(product.price_kopecks||0)<Number(previous.price_kopecks||0))) groups.set(key,product);
  });
  return [...groups.values()];
}
function normalizeStoreSearchText(value){
  return String(value || '')
    .toLowerCase()
    .replace(/ё/g, 'е')
    .replace(/([a-zа-я])(\d)/gi, '$1 $2')
    .replace(/(\d)([a-zа-я])/gi, '$1 $2')
    .replace(/[^0-9a-zа-я\s]+/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}
function storeTokenMaxDist(len){
  const n = Number(len || 0);
  if (n <= 2) return 0;
  if (n <= 4) return 1;
  if (n <= 7) return 2;
  if (n <= 12) return 3;
  return 4;
}
function storeEditDistance(a, b, maxDist){
  const left = String(a || '');
  const right = String(b || '');
  if (left === right) return 0;
  const la = left.length;
  const lb = right.length;
  if (Math.abs(la - lb) > maxDist) return maxDist + 1;
  if (!la) return lb;
  if (!lb) return la;
  let prev = Array.from({ length: lb + 1 }, (_, i) => i);
  for (let i = 1; i <= la; i += 1) {
    const cur = [i];
    let rowMin = i;
    for (let j = 1; j <= lb; j += 1) {
      const cost = left[i - 1] === right[j - 1] ? 0 : 1;
      const next = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost);
      cur.push(next);
      if (next < rowMin) rowMin = next;
    }
    if (rowMin > maxDist) return maxDist + 1;
    prev = cur;
  }
  return prev[lb];
}
function storeFuzzyTokenScore(queryToken, candidateToken){
  if (!queryToken || !candidateToken) return 0;
  if (candidateToken === queryToken) return 1;
  if (candidateToken.startsWith(queryToken)) return 0.94;
  if (candidateToken.includes(queryToken)) return 0.88;
  if (queryToken.length >= 3 && queryToken.includes(candidateToken) && candidateToken.length >= 3) return 0.72;
  if (queryToken.length >= 3 && candidateToken.length > queryToken.length) {
    const prefix = candidateToken.slice(0, queryToken.length);
    const maxPrefixDist = storeTokenMaxDist(queryToken.length);
    const prefixDist = storeEditDistance(queryToken, prefix, maxPrefixDist);
    if (prefixDist <= maxPrefixDist) {
      return Math.max(0.68, 1 - prefixDist / Math.max(queryToken.length, 1));
    }
  }
  const maxDist = storeTokenMaxDist(queryToken.length);
  if (maxDist <= 0) return 0;
  if (Math.abs(candidateToken.length - queryToken.length) > maxDist) return 0;
  const dist = storeEditDistance(queryToken, candidateToken, maxDist);
  if (dist > maxDist) return 0;
  return Math.max(0.48, 1 - dist / Math.max(queryToken.length, candidateToken.length, 1));
}
function storeFuzzyWindowScore(query, hay){
  if (!query || !hay) return 0;
  if (hay.includes(query)) return 1;
  const qCompact = query.replace(/\s+/g, '');
  const hCompact = hay.replace(/\s+/g, '');
  if (!qCompact || !hCompact) return 0;
  if (hCompact.includes(qCompact)) return 0.98;
  if (qCompact.length < 3) return 0;
  const maxDist = storeTokenMaxDist(qCompact.length);
  if (maxDist <= 0) return 0;
  let best = 0;
  const minW = Math.max(3, qCompact.length - maxDist);
  const maxW = qCompact.length + maxDist;
  for (let w = minW; w <= maxW; w += 1) {
    if (w > hCompact.length) break;
    for (let i = 0; i + w <= hCompact.length; i += 1) {
      const slice = hCompact.slice(i, i + w);
      if (Math.abs(slice.length - qCompact.length) > maxDist) continue;
      const dist = storeEditDistance(qCompact, slice, maxDist);
      if (dist <= maxDist) {
        best = Math.max(best, 1 - dist / Math.max(qCompact.length, 1));
        if (best >= 0.95) return best;
      }
    }
  }
  return best >= 0.5 ? best : 0;
}
function storeProductSearchScore(product, query){
  const q = normalizeStoreSearchText(query);
  if (!q) return 1;
  const hay = normalizeStoreSearchText([product?.title,storeVariantGroup(product),product?.variant_label].filter(Boolean).join(' '));
  if (!hay) return 0;
  if (hay.includes(q)) return 1;
  const windowScore = storeFuzzyWindowScore(q, hay);
  const qTokens = q.split(' ').filter(Boolean);
  const cTokens = hay.split(' ').filter(Boolean);
  if (!qTokens.length) return 1;
  const tokenScores = [];
  for (const qt of qTokens) {
    let best = 0;
    for (const ct of cTokens) best = Math.max(best, storeFuzzyTokenScore(qt, ct));
    for (let i = 0; i < cTokens.length - 1; i += 1) {
      best = Math.max(best, storeFuzzyTokenScore(qt, `${cTokens[i]}${cTokens[i + 1]}`));
      best = Math.max(best, storeFuzzyTokenScore(qt, `${cTokens[i]} ${cTokens[i + 1]}`));
    }
    tokenScores.push(best);
  }
  const matched = tokenScores.filter((score) => score >= 0.5).length;
  const avg = tokenScores.reduce((sum, score) => sum + score, 0) / tokenScores.length;
  const need = Math.max(1, Math.ceil(qTokens.length * 0.6));
  const majorityOk = matched >= need && avg >= 0.45;
  const allSoft = tokenScores.every((score) => score >= 0.42) && avg >= 0.48;
  const tokenScore = (majorityOk || allSoft) ? Math.max(avg, matched / qTokens.length * 0.88) : 0;
  return Math.max(windowScore * 0.96, tokenScore);
}
function productMatchesCategory(product, categoryId){
  const cat = String(categoryId || '').trim();
  if (!cat) return true;
  if (String(product?.category_id || '') === cat) return true;
  return Array.isArray(product?.category_ids) && product.category_ids.some((id) => String(id) === cat);
}
function getVisibleStoreProducts(){
  const categoryId = state.store.categoryFilter || '';
  const query = state.store.searchQuery || '';
  let list = Array.isArray(state.store.products) ? state.store.products.slice() : [];
  if (categoryId) list = list.filter((p) => productMatchesCategory(p, categoryId));
  if (normalizeStoreSearchText(query)) {
    list = list
      .map((p) => ({ product: p, score: storeProductSearchScore(p, query) }))
      .filter((row) => row.score >= 0.45)
      .sort((a, b) => b.score - a.score || Number(a.product.sort_order || 0) - Number(b.product.sort_order || 0) || Number(a.product.id || 0) - Number(b.product.id || 0))
      .map((row) => row.product);
  }
  return groupStoreCatalogProducts(list,Boolean(normalizeStoreSearchText(query))).map(product=>{
    const group=storeVariantGroup(product);
    const selection=group&&state.store.catalogVariantSelection?.get(group);
    if(!selection || selection.context!==storeCatalogVariantContext()) return product;
    return (state.store.products||[]).find(candidate=>Number(candidate.id)===selection.productId
      && storeVariantGroup(candidate)===group && Number(candidate.is_active??1)===1) || product;
  });
}
function storeCatalogVariantContext(){
  return JSON.stringify([state.store.categoryFilter||'',normalizeStoreSearchText(state.store.searchQuery)]);
}
function buildStoreCatalogCard(initialProduct){
  const card=document.createElement('article');
  card.className='equipment-card store-product-card';
  card.tabIndex=0;
  let currentProduct=initialProduct;
  card.addEventListener('click',()=>openStoreProductDialog(currentProduct));
  card.addEventListener('keydown',e=>{
    if(e.target===card && (e.key==='Enter'||e.key===' ')){
      e.preventDefault();
      void openStoreProductDialog(currentProduct);
    }
  });
  function renderSelection(product){
    currentProduct=product;
    card.dataset.productId=String(product.id);
    setHtml(card,'');
    if(product.photo_url){
      const img=document.createElement('img');
      img.src=product.photo_url; img.alt=storeProductCartTitle(product); img.loading='lazy';
      card.appendChild(img);
    }
    const title=document.createElement('h3'); title.textContent=storeVariantGroup(product)||product.title||'Товар';
    const meta=document.createElement('p'); meta.className='store-product-card__meta'; meta.textContent=product.category_name||'Магазин';
    card.append(title,meta);
    const variants=getStoreProductVariants(product);
    if(variants.length){
      const choices=document.createElement('div'); choices.className='store-card-variants'; choices.setAttribute('aria-label','Варианты товара');
      variants.forEach(variant=>{
        const selected=Number(variant.id)===Number(product.id);
        const chip=document.createElement('button'); chip.type='button'; chip.className=`store-card-variant${selected?' is-active':''}`;
        chip.dataset.storeCatalogVariantId=String(variant.id);
        chip.setAttribute('aria-pressed',String(selected));
        chip.textContent=storeVariantLabel(variant); chip.title=`${storeVariantLabel(variant)} · ${variant.price_text||moneyRub(variant.price_kopecks||0)}`;
        chip.onclick=e=>{
          e.stopPropagation();
          if(!state.store.catalogVariantSelection) state.store.catalogVariantSelection=new Map();
          state.store.catalogVariantSelection.set(storeVariantGroup(variant),{productId:Number(variant.id),context:storeCatalogVariantContext()});
          renderSelection(variant);
          card.querySelector(`[data-store-catalog-variant-id="${Number(variant.id)}"]`)?.focus({preventScroll:true});
        };
        choices.appendChild(chip);
      });
      card.appendChild(choices);
    }
    card.appendChild(buildProductPriceEl(product));
    card.appendChild(buildStoreAction(product));
    const rating=document.createElement('div'); rating.className='store-rating';
    rating.textContent=`${renderRatingStars(product.rating_average)} ${formatRatingAverage(product.rating_average)} (${Number(product.review_count||0)})`;
    card.appendChild(rating);
  }
  renderSelection(initialProduct);
  return card;
}
function renderStore(){
  const grid=document.getElementById('storeGrid'); const cats=document.getElementById('storeCategories');
  if(!grid) return; setHtml(grid,'');
  if(cats){
    setHtml(cats,'');
    const allBtn=document.createElement('button');
    allBtn.type='button';
    allBtn.className=`chip${!state.store.categoryFilter ? ' is-active' : ''}`;
    allBtn.textContent=`Все (${groupStoreCatalogProducts(state.store.products||[]).length})`;
    allBtn.onclick=()=>setStoreCategory('');
    cats.appendChild(allBtn);
    (state.store.categories||[]).forEach(c=>{
      const b=document.createElement('button');
      b.type='button';
      b.className=`chip${String(state.store.categoryFilter||'')===String(c.id||'') ? ' is-active' : ''}`;
      b.textContent=`${c.title} (${groupStoreCatalogProducts((state.store.products||[]).filter(p=>productMatchesCategory(p,c.id))).length})`;
      b.onclick=()=>setStoreCategory(c.id);
      cats.appendChild(b);
    });
  }
  const products=getVisibleStoreProducts();
  if(!(state.store.products||[]).length){ grid.innerHTML='<div class="muted">Товары пока не добавлены.</div>'; renderCart(); return; }
  if(!products.length){ grid.innerHTML='<div class="muted">Ничего не найдено. Попробуйте другой запрос или категорию.</div>'; renderCart(); return; }
  products.forEach(product=>grid.appendChild(buildStoreCatalogCard(product)));
  renderCart();
}
function renderRatingStars(value){ const rating=Number(value||0); const rounded=Math.max(0,Math.min(5,Math.round(Number.isFinite(rating)?rating:0))); return '★'.repeat(rounded)+'☆'.repeat(5-rounded); }
function formatRatingAverage(value){ const rating=Number(value||0); return Number.isFinite(rating) ? rating.toFixed(1) : '0.0'; }
function reviewAuthorName(review){ const name=String(review?.user_nickname||review?.username||'').trim(); return name || (Number(review?.user_id||0) ? `Пользователь #${Number(review.user_id)}` : 'Пользователь'); }
function reviewMeaningfulCharacterCount(value){
  const chars=String(value||'').normalize('NFKC').match(/[\p{L}\p{N}]/gu);
  return chars ? chars.length : 0;
}
function reviewBonusEligibilityMessage(eligibility){
  const info=eligibility||{};
  const current=Math.max(0,Number(info.meaningful_characters||0));
  const minimum=Math.max(100,Number(info.minimum_characters||100));
  if(info.reason==='too_repetitive') return 'Текст слишком однообразный. Опишите личный опыт разными словами.';
  if(info.reason==='empty') return `Для бонуса добавьте содержательный отзыв от ${minimum} символов.`;
  return `Для бонуса нужно не менее ${minimum} содержательных символов без учёта пробелов и знаков. Сейчас: ${current}.`;
}
function storeReviewsHtml(detail){
  const rawReviews=Array.isArray(detail.reviews)?detail.reviews:[];
  const mine=rawReviews.find(r=>r.is_mine)||null;
  const canEditReview=Boolean(detail.can_review||mine);
  const orderedReviews=mine ? [mine,...rawReviews.filter(r=>!r.is_mine)] : rawReviews;
  const reviews=orderedReviews.map(r=>`<article class="store-review${r.is_mine?' store-review--mine':''}"><div class="store-review__head"><strong class="store-review__author">${escapeHtml(reviewAuthorName(r))}</strong><span class="store-review__rating"><b>${renderRatingStars(r.rating)}</b>${r.is_mine&&canEditReview?'<button type="button" class="store-review__edit" data-review-edit aria-label="Редактировать отзыв" title="Редактировать отзыв">✎</button>':''}</span></div>${Number(r.review_bonus_kopecks||0)>0?`<small class="store-review__bonus">Начислено ${sausageCountText(r.review_bonus_kopecks)}</small>`:''}<p>${r.review_text?escapeHtml(r.review_text):'<span class="muted">Без текста</span>'}</p></article>`).join('');
  const formHidden=Boolean(mine);
  const reviewBonus=storeReviewBonusSettings();
  const minimum=Math.max(100,Number(reviewBonus.minimum_characters||100));
  const amount=Math.max(0,Number(reviewBonus.sausages||20));
  const alreadyRewarded=Number(mine?.review_bonus_kopecks||0)>0;
  const bonusHint=alreadyRewarded
    ? `Бонус уже начислен. При редактировании оставьте не менее ${minimum} содержательных символов.`
    : (reviewBonus.is_active
      ? `За содержательный отзыв от ${minimum} символов начислим ${amount.toLocaleString('ru-RU')} колбасок. Пробелы, знаки и однообразный набор не засчитываются.`
      : 'Акция за отзывы сейчас приостановлена. Оценку и отзыв можно сохранить без бонуса.');
  const initialCount=reviewMeaningfulCharacterCount(mine?.review_text||'');
  const form=canEditReview?`<form class="store-review-form${formHidden?' hidden':''}" data-product-id="${detail.id}" data-review-minimum="${minimum}"><label>Ваша оценка <select name="rating">${[5,4,3,2,1].map(n=>`<option value="${n}"${Number(mine?.rating)===n?' selected':''}>${n} ★</option>`).join('')}</select></label><textarea name="review_text" maxlength="4000" aria-describedby="storeReviewBonusHint-${detail.id}" placeholder="Расскажите, что понравилось и как использовали товар">${escapeHtml(mine?.review_text||'')}</textarea><div class="store-review-form__meta"><small id="storeReviewBonusHint-${detail.id}">${escapeHtml(bonusHint)}</small><span data-review-counter>${initialCount.toLocaleString('ru-RU')} / ${minimum.toLocaleString('ru-RU')}</span></div><button type="submit">${mine?'Сохранить изменения':'Сохранить отзыв'}</button></form>`:'';
  return `<section class="store-reviews"><h4>Отзывы (${Number(detail.review_count||0)})</h4>${form}${reviews||'<p class="muted">Отзывов пока нет.</p>'}</section>`;
}
function bindStoreReviewForm(item){
  if(!el.equipmentDialogReviews) return;
  el.equipmentDialogReviews.querySelector('[data-review-edit]')?.addEventListener('click',(event)=>{
    event.preventDefault();
    const form=el.equipmentDialogReviews.querySelector('.store-review-form');
    form?.classList.remove('hidden');
    form?.querySelector('textarea')?.focus();
  });
  const form=el.equipmentDialogReviews.querySelector('.store-review-form');
  if(!form) return;
  const textarea=form.querySelector('textarea[name="review_text"]');
  const counter=form.querySelector('[data-review-counter]');
  const updateCounter=()=>{
    if(!counter) return;
    const count=reviewMeaningfulCharacterCount(textarea?.value||'');
    const minimum=Math.max(100,Number(form.dataset.reviewMinimum||100));
    counter.textContent=`${count.toLocaleString('ru-RU')} / ${minimum.toLocaleString('ru-RU')}`;
    counter.classList.toggle('is-ready',count>=minimum);
  };
  textarea?.addEventListener('input',updateCounter);
  updateCounter();
  form.addEventListener('submit',async(event)=>{
    event.preventDefault();
    const submit=form.querySelector('button[type="submit"]');
    setButtonBusy(submit,true,'Сохраняем…');
    try{
      const result=await api(`/api/store/products/${item.id}/review`,{method:'PUT',body:{rating:Number(form.rating.value),review_text:form.review_text.value}});
      if(result.rewards) state.store.rewards=result.rewards;
      renderCart();
      renderAccountInfo();
      if(result.review_bonus_granted){
        showToast(`Отзыв сохранён. Начислено ${Number(result.review_bonus_sausages||0).toLocaleString('ru-RU')} колбасок!`);
      }else if(!result.review_bonus_eligibility?.eligible){
        showToast(`Отзыв сохранён без бонуса. ${reviewBonusEligibilityMessage(result.review_bonus_eligibility)}`,true);
      }else if(!storeReviewBonusSettings().is_active){
        showToast('Отзыв сохранён. Акция за отзывы сейчас приостановлена.');
      }else{
        showToast('Отзыв сохранён. Бонус за этот товар уже начислялся.');
      }
      await openStoreProductDialog(item);
    }catch(error){
      showToast(errorText(error,'Не удалось сохранить отзыв.'),true);
    }finally{
      setButtonBusy(submit,false);
    }
  });
}
function storeProductDialogHtml(item, detail=null, description=''){
  const desc=description || item?.description || 'Описание отсутствует.';
  const variants=getStoreProductVariants(item,detail);
  const choices=variants.length ? `<fieldset class="store-variant-picker"><legend>Вариант: <strong>${escapeHtml(storeVariantLabel(item))}</strong></legend><div class="store-variant-picker__options">${variants.map(variant=>`<button type="button" class="store-variant-option${Number(variant.id)===Number(item.id)?' is-active':''}" data-store-variant-id="${Number(variant.id)}" aria-pressed="${Number(variant.id)===Number(item.id)}"><span>${escapeHtml(storeVariantLabel(variant))}</span><small>${escapeHtml(variant.price_text||moneyRub(variant.price_kopecks||0))}</small></button>`).join('')}</div></fieldset>` : '';
  const characteristics=[];
  if(!isStoreProductDigital(item)){
    if(Number(item.weight_grams)>0) characteristics.push(['Вес для доставки',`${Number(item.weight_grams).toLocaleString('ru-RU')} г`]);
    if(String(item.length||'').trim()) characteristics.push(['Длина',`${item.length} м`]);
    if(String(item.dimensions||'').trim()) characteristics.push(['Габариты',`${item.dimensions} см`]);
  }
  const specs=characteristics.length ? `<dl class="store-product-specs">${characteristics.map(([label,value])=>`<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`).join('')}</dl>` : '';
  return `<section class="store-detail-main">${choices}${productPriceHtml(item)}${specs}<div class="equipment-dialog__desc">${escapeHtml(desc)}</div></section>`;
}
function bindStoreVariantPicker(item,detail=null){
  const variants=getStoreProductVariants(item,detail);
  el.equipmentDialogText.querySelectorAll('[data-store-variant-id]').forEach(button=>{
    button.onclick=()=>{
      const variant=variants.find(candidate=>Number(candidate.id)===Number(button.dataset.storeVariantId));
      if(variant && Number(variant.id)!==Number(item.id)) void openStoreProductDialog(variant);
    };
  });
}
function isCurrentStoreProductDialog(productId){
  return Number(el.equipmentDialog?.dataset?.productId||0)===Number(productId||0);
}
function setStoreCategory(categoryId=''){
  state.store.categoryFilter = String(categoryId || '');
  renderStore();
}
function scheduleStoreSearch(value){
  state.store.searchQuery = String(value || '');
  if (storeSearchTimer) clearTimeout(storeSearchTimer);
  storeSearchTimer = setTimeout(() => {
    storeSearchTimer = null;
    renderStore();
  }, 120);
}
function storeProductIdFromUrl(){
  const params=new URLSearchParams(window.location.search || '');
  return Number(params.get('product') || params.get('store_product') || 0);
}
function openStoreProductFromUrl(){
  const productId=storeProductIdFromUrl();
  if(!productId) return false;
  const product=resolveStoreProductById(productId);
  if(!product){
    showToast('Товар по ссылке не найден.', true);
    return false;
  }
  const gate=canOpenTab('home');
  if(!gate.ok){
    openPaywall(gate.reason);
    return false;
  }
  activateTab('home');
  openStoreProductDialog(product);
  return true;
}
function visibleStorePromoSlides(){
  return Array.from(el.storePromoBanner?.querySelectorAll('[data-store-promo-slide]')||[])
    .filter((slide)=>!slide.classList.contains('hidden'));
}
function stopStorePromoCarousel(){
  if(storePromoCarouselTimer){
    window.clearInterval(storePromoCarouselTimer);
    storePromoCarouselTimer=null;
  }
}
function showStorePromoSlide(index){
  const slides=visibleStorePromoSlides();
  if(!slides.length) return;
  const nextIndex=((Number(index||0)%slides.length)+slides.length)%slides.length;
  state.store.promoCarouselIndex=nextIndex;
  const activeSlide=slides[nextIndex];
  el.storePromoBanner.querySelectorAll('[data-store-promo-slide]').forEach((slide)=>{
    const active=slide===activeSlide;
    slide.classList.toggle('is-active', active);
    slide.setAttribute('aria-hidden', active?'false':'true');
  });
  el.storePromoBanner.querySelectorAll('[data-store-promo-dot]').forEach((dot)=>{
    const belongs=String(dot.dataset.storePromoDot||'')===String(activeSlide.dataset.storePromoSlide||'');
    dot.classList.toggle('is-active', belongs);
    dot.setAttribute('aria-current', belongs?'true':'false');
  });
  const single=slides.length<2;
  if(el.storePromoPrev) el.storePromoPrev.disabled=single;
  if(el.storePromoNext) el.storePromoNext.disabled=single;
}
function startStorePromoCarousel(){
  stopStorePromoCarousel();
  if(visibleStorePromoSlides().length<2) return;
  storePromoCarouselTimer=window.setInterval(()=>{
    showStorePromoSlide(state.store.promoCarouselIndex+1);
  },10000);
}
function moveStorePromoCarousel(delta){
  showStorePromoSlide(state.store.promoCarouselIndex+Number(delta||0));
  startStorePromoCarousel();
}
function renderStorePromoBanner(){
  if(!el.storePromoBanner) return;
  if(Array.isArray(state.store.banners)){
    renderManagedStoreBanners(state.store.banners);
    return;
  }
  const saus=sausagePromoSettings();
  const reviewBonus=storeReviewBonusSettings();
  const referral=state.store.referralSettings||{};
  const sausageVisible=Boolean(state.store.promoBanner?.visible);
  const reviewBonusVisible=Boolean(state.store.promoBanner?.review_bonus_visible ?? reviewBonus.is_active);
  const referralVisible=Boolean(state.store.promoBanner?.referral_visible ?? referral.is_active);
  const standardPercent=Math.max(0,Math.min(100,Number(saus.standard_percent??saus.percent??5)));
  const premiumPercent=Math.max(0,Math.min(100,Number(saus.premium_percent??10)));
  const referralPercent=Math.max(0,Math.min(100,Number(referral.percent??5)));
  const reviewBonusAmount=Math.max(0,Number(reviewBonus.sausages??20));
  const reviewBonusMinChars=Math.max(100,Number(reviewBonus.minimum_characters??100));
  const standardNode=document.getElementById('storePromoStandardPercent');
  const premiumNode=document.getElementById('storePromoPremiumPercent');
  const referralNode=document.getElementById('storeReferralPercent');
  const reviewBonusAmountNode=document.getElementById('storeReviewBonusAmount');
  const reviewBonusMinCharsNode=document.getElementById('storeReviewBonusMinChars');
  if(standardNode) standardNode.textContent=String(standardPercent);
  if(premiumNode) premiumNode.textContent=String(premiumPercent);
  if(referralNode) referralNode.textContent=String(referralPercent);
  if(reviewBonusAmountNode) reviewBonusAmountNode.textContent=reviewBonusAmount.toLocaleString('ru-RU');
  if(reviewBonusMinCharsNode) reviewBonusMinCharsNode.textContent=reviewBonusMinChars.toLocaleString('ru-RU');
  const sausageSlide=el.storePromoBanner.querySelector('[data-store-promo-slide="sausages"]');
  const referralSlide=el.storePromoBanner.querySelector('[data-store-promo-slide="referral"]');
  const reviewBonusSlide=el.storePromoBanner.querySelector('[data-store-promo-slide="review-bonus"]');
  const sausageDot=el.storePromoBanner.querySelector('[data-store-promo-dot="sausages"]');
  const referralDot=el.storePromoBanner.querySelector('[data-store-promo-dot="referral"]');
  const reviewBonusDot=el.storePromoBanner.querySelector('[data-store-promo-dot="review-bonus"]');
  sausageSlide?.classList.toggle('hidden',!sausageVisible);
  referralSlide?.classList.toggle('hidden',!referralVisible);
  reviewBonusSlide?.classList.toggle('hidden',!reviewBonusVisible);
  sausageDot?.classList.toggle('hidden',!sausageVisible);
  referralDot?.classList.toggle('hidden',!referralVisible);
  reviewBonusDot?.classList.toggle('hidden',!reviewBonusVisible);
  const visible=sausageVisible||referralVisible||reviewBonusVisible;
  el.storePromoBanner.classList.toggle('hidden',!visible);
  if(!visible){ stopStorePromoCarousel(); return; }
  showStorePromoSlide(Math.min(state.store.promoCarouselIndex,visibleStorePromoSlides().length-1));
  startStorePromoCarousel();
}
async function loadStore(){
  const data=await api('/api/store');
  state.store.products=data.products||[];
  state.store.categories=data.categories||[];
  state.store.deliveryMethods=data.delivery_methods||[];
  state.store.deliverySizes=data.delivery_sizes||[];
  state.store.promoSettings=data.promo_settings||data.store_promo_settings||state.store.promoSettings||{};
  state.store.reviewBonusSettings=data.review_bonus_settings||state.store.reviewBonusSettings||{};
  state.store.referralSettings=data.referral_settings||state.store.referralSettings||{};
  state.store.storeSettings=data.store_settings||state.store.storeSettings||{};
  state.store.rewards=data.rewards||state.store.rewards||{};
  state.store.premiumActive=Boolean(data.premium_active);
  state.store.paidPremiumCashback=Boolean(data.paid_premium_cashback);
  state.store.premiumDiscountPercent=Number(data.premium_discount_percent||0);
  state.store.promoBanner=data.promo_banner||{};
  state.store.banners=Array.isArray(data.banners)?data.banners:null;
  state.store.ozonDelivery={
    ...(state.store.ozonDelivery||{}),
    enabled:Boolean(data.ozon_delivery?.enabled),
    pointsReady:Boolean(data.ozon_delivery?.points_ready),
    checkoutEnabled:Boolean(data.ozon_delivery?.checkout_enabled),
  };
  if (state.store.categoryFilter && !(state.store.categories||[]).some((c)=>String(c.id)===String(state.store.categoryFilter))) {
    state.store.categoryFilter = '';
  }
  // Подтянуть актуальный вес товаров в строки корзины (вес × кол-во для доставки).
  state.store.cart = sanitizeStoreCart(state.store.cart||[]);
  reconcileSausageRedemption();
  renderStorePromoBanner();
  renderStore();
  renderAccountInfo();
}
function addToCart(p, options={}){
  const cart=state.store.cart||[];
  const addQuantity = Math.max(1, Math.ceil(Number(options.quantity || 1)));
  const row=cart.find(i=>Number(i.product_id)===Number(p.id));
  const weightGrams=Number(p.shipping_weight_grams||p.weight_grams||0)||resolveCartItemShippingWeightGrams(p)||0;
  if(row){
    row.quantity=Math.min(999, Number(row.quantity||0)+addQuantity);
    row.weight_grams=weightGrams;
    row.shipping_weight_grams=weightGrams;
    row.weight_kg=weightGrams/1000;
    row.weight=String(weightGrams||p.weight||'');
    if(p.price_kopecks!=null) row.price_kopecks=Number(p.price_kopecks);
    row.base_price_kopecks=Number(p.base_price_kopecks??p.price_kopecks??row.base_price_kopecks??0);
    row.base_price_text=String(p.base_price_text||row.base_price_text||'');
    row.price_text=String(p.price_text||row.price_text||'');
    row.premium_discount_percent=Number(p.premium_discount_percent||0);
    if(p.title) row.title=storeProductCartTitle(p);
    if(p.photo_url) row.photo_url=p.photo_url;
  } else {
    cart.push({product_id:p.id,title:storeProductCartTitle(p),base_price_kopecks:p.base_price_kopecks,base_price_text:p.base_price_text,price_kopecks:p.price_kopecks,price_text:p.price_text,premium_discount_percent:p.premium_discount_percent,photo_url:p.photo_url,weight:String(weightGrams||p.weight||''),weight_kg:weightGrams/1000,weight_grams:weightGrams,shipping_weight_grams:weightGrams,quantity:addQuantity});
  }
  state.store.cart=cart;
  reconcileSausageRedemption();
  saveStoreCart();
  renderStore();
  if(!options.silent) showToast('Товар добавлен в корзину.');
}
function renderStoreProductDialogSelection(item,detail=null,description=''){
  const title=storeVariantGroup(item)||item?.title||'Товар';
  const category=String(item?.category_name||'Магазин').trim()||'Магазин';
  setHtml(el.equipmentDialogTitle, `<span class="store-product-dialog__eyebrow">${escapeHtml(category)}</span><span>${escapeHtml(title)}</span>`);
  setHtml(el.equipmentDialogText, storeProductDialogHtml(item,detail,description));
  bindStoreVariantPicker(item,detail);
  if(item.photo_url){
    el.equipmentDialog.classList.remove('dialog--store-product-no-image');
    el.equipmentDialogImage.src=item.photo_url;
    el.equipmentDialogImage.alt=storeProductCartTitle(item);
    el.equipmentDialogImage.classList.remove('hidden');
  }
  else {
    el.equipmentDialog.classList.add('dialog--store-product-no-image');
    el.equipmentDialogImage.classList.add('hidden');
    el.equipmentDialogImage.removeAttribute('src');
  }
  if(el.equipmentDialogShare){
    el.equipmentDialogShare.classList.remove('hidden');
    el.equipmentDialogShare.textContent='Поделиться';
    el.equipmentDialogShare.onclick=(e)=>{ e.preventDefault(); void copyStoreProductLink(item, el.equipmentDialogShare); };
  }
  el.equipmentDialogLink.classList.remove('hidden');
  el.equipmentDialogLink.href='#';
  el.equipmentDialogLink.removeAttribute('target');
  el.equipmentDialogLink.removeAttribute('rel');
  el.equipmentDialogLink.setAttribute('role','button');
  el.equipmentDialogLink.setAttribute('tabindex','0');
  el.equipmentDialogLink.textContent='Добавить в корзину';
  el.equipmentDialogLink.onclick=(e)=>{ e.preventDefault(); addToCart(item); if(typeof el.equipmentDialog.close==='function') el.equipmentDialog.close(); };
}
async function openStoreProductDialog(item){
  const productId=Number(item?.id||0);
  if(!productId) return;
  const requestId=Number(state.store.productDialogRequestId||0)+1;
  state.store.productDialogRequestId=requestId;
  resetEquipmentDialogScroll();
  el.equipmentDialog.classList.add('dialog--store-product');
  el.equipmentDialog.dataset.productId=String(productId);
  renderStoreProductDialogSelection(item,null,item.description||'Загрузка описания…');
  if(el.equipmentDialogReviews){
    setHtml(el.equipmentDialogReviews, '<section class="store-reviews"><h4>Отзывы</h4><p class="muted">Загружаем отзывы…</p></section>');
    el.equipmentDialogReviews.classList.remove('hidden');
  }
  renderStoreRelatedDialog(resolveRelatedProductsFromCatalog(item), item.id);
  if(typeof el.equipmentDialog.showModal==='function' && !el.equipmentDialog.open) el.equipmentDialog.showModal();
  resetEquipmentDialogScrollAfterLayout();
  try {
    const detail = await api(`/api/store/products/${encodeURIComponent(item.id)}`);
    if (Number(detail?.id) !== productId || state.store.productDialogRequestId!==requestId || !isCurrentStoreProductDialog(productId)) return;
    Object.assign(item,detail,{description:detail.description||'Описание отсутствует.'});
    const idx = (state.store.products||[]).findIndex((p)=>Number(p.id)===Number(item.id));
    if (idx >= 0) {
      Object.assign(state.store.products[idx],item);
    }
    if (isCurrentStoreProductDialog(item.id)) {
      renderStoreProductDialogSelection(item,detail,item.description);
      if(el.equipmentDialogReviews){
        setHtml(el.equipmentDialogReviews, storeReviewsHtml(detail));
        el.equipmentDialogReviews.classList.remove('hidden');
        bindStoreReviewForm(item);
      }
      renderStoreRelatedDialog(detail.related_products || resolveRelatedProductsFromCatalog(item), item.id);
    }
  } catch (_error) {
    if(state.store.productDialogRequestId!==requestId) return;
    if (isCurrentStoreProductDialog(item.id) && !item.description) {
      renderStoreProductDialogSelection(item,null,'Описание недоступно.');
    }
    if (isCurrentStoreProductDialog(item.id) && el.equipmentDialogReviews) {
      setHtml(el.equipmentDialogReviews, '<section class="store-reviews"><h4>Отзывы</h4><p class="muted">Отзывы временно недоступны.</p></section>');
      el.equipmentDialogReviews.classList.remove('hidden');
    }
  }
}
function clearStoreRelatedDialog(){
  const box=document.getElementById('equipmentDialogRelated');
  if(!box) return;
  setHtml(box,'');
  box.classList.add('hidden');
}
function resolveRelatedProductsFromCatalog(item){
  const ids=Array.isArray(item?.related_ids)?item.related_ids:[];
  const out=[];
  const seen=new Set();
  ids.forEach((raw)=>{
    const id=Number(raw||0);
    if(!id || seen.has(id) || id===Number(item?.id||0)) return;
    seen.add(id);
    const product=catalogProductById(id) || (state.store.products||[]).find((p)=>Number(p.id)===id);
    if(product) out.push(product);
  });
  return out;
}
function renderStoreRelatedDialog(products, currentId){
  const box=document.getElementById('equipmentDialogRelated');
  if(!box) return;
  const list=(products||[]).filter((p)=>Number(p?.id||0)>0 && Number(p.id)!==Number(currentId||0));
  if(!list.length){
    clearStoreRelatedDialog();
    return;
  }
  box.classList.remove('hidden');
  setHtml(box,'<h4>С этим товаром покупают</h4><div class="store-related__grid"></div>');
  const grid=box.querySelector('.store-related__grid');
  list.forEach((p)=>{
    const card=document.createElement('button');
    card.type='button';
    card.className='store-related__card';
    if(p.photo_url){
      const img=document.createElement('img');
      img.src=p.photo_url;
      img.alt=p.title||'Товар';
      img.loading='lazy';
      card.appendChild(img);
    }
    const title=document.createElement('strong');
    title.textContent=p.title||'Товар';
    card.append(title, buildProductPriceEl(p));
    card.onclick=(e)=>{
      e.preventDefault();
      e.stopPropagation();
      openStoreProductDialog(p);
    };
    grid.appendChild(card);
  });
}
function renderCartRewardsControl(){
  const control=document.getElementById('cartRewardsControl');
  const hint=document.getElementById('cartRewardsHint');
  const button=document.getElementById('cartSausageToggleBtn');
  if(!control || !hint || !button) return;
  reconcileSausageRedemption();
  const balance=Math.max(0, Number((state.store.rewards||{}).sausages_kopecks||0));
  const limit=cartSausageRedemptionLimit();
  const visible=Boolean(cartCount() && isSausagePromoActive() && balance>0 && limit>0);
  control.classList.toggle('hidden', !visible);
  if(!visible) return;
  hint.textContent=`Баланс: ${sausageCountText(balance)}. К заказу можно применить ${sausageCountText(limit)}.`;
  button.classList.toggle('is-active', Boolean(state.store.useSausages));
  button.textContent=state.store.useSausages
    ? 'Не списывать колбаски'
    : `Списать ${sausageCountText(limit)}`;
}
function checkoutFormDraft(){
  const form=document.getElementById('checkoutForm');
  if(!form) return null;
  const values={};
  ['customer_name','phone','address','digital_delivery_email'].forEach((name)=>{
    const field=form.elements.namedItem(name);
    if(field && typeof field.value==='string') values[name]=field.value;
  });
  ['accept_public_offer','accept_privacy_policy','accept_age_18'].forEach((name)=>{
    const field=form.elements.namedItem(name);
    if(field && typeof field.checked==='boolean') values[name]=field.checked;
  });
  return values;
}
function restoreCheckoutFormDraft(draft){
  if(!draft) return;
  const form=document.getElementById('checkoutForm');
  if(!form) return;
  Object.entries(draft).forEach(([name,value])=>{
    const field=form.elements.namedItem(name);
    if(!field) return;
    if(typeof value==='boolean' && typeof field.checked==='boolean') field.checked=value;
    else if(typeof field.value==='string') field.value=value;
  });
}
function renderCart(){ const checkoutDraft=checkoutFormDraft(); const restoreCheckoutDraft=()=>restoreCheckoutFormDraft(checkoutDraft); const wrap=document.getElementById('cartItems'), sum=document.getElementById('cartSummary'), total=document.getElementById('cartTotal'), deliveryEl=document.getElementById('cartDeliveryPrice'), sel=document.getElementById('deliveryMethodSelect'), cartCard=document.getElementById('cartCard'); updateDigitalDeliveryEmailField(); if(sum){ sum.textContent=`Корзина · ${cartCount()}`; sum.setAttribute('aria-label', `Открыть корзину. Товаров: ${cartCount()}`); } if(sel){ const cur=sel.value; setHtml(sel,''); (state.store.deliveryMethods||[]).forEach(d=>{const o=document.createElement('option'); o.value=d.code; const unavailable=String(d.code||'')==='ozon'&&!state.store.ozonDelivery?.enabled; o.disabled=unavailable; o.textContent=`${d.title||d.code}${unavailable?' — недоступно':''}`; sel.appendChild(o);}); const previous=Array.from(sel.options).find((o)=>o.value===cur&&!o.disabled); const fallback=Array.from(sel.options).find((o)=>!o.disabled); if(previous) sel.value=previous.value; else if(fallback) sel.value=fallback.value; }
  if(state.store.ozonDelivery?.quote && !currentOzonQuote()) invalidateOzonDeliveryQuote();
  updateOzonDeliveryUi();
  updateCheckoutAuthUi();
  const legacyQuote=calculateStoreDeliveryQuote(cartWeightGrams());
  const ozonQuote=currentOzonQuote();
  const ozonEstimate=currentOzonDeliveryEstimate();
  const deliveryPrice=ozonQuoteRequired()?Number(ozonQuote?.price_kopecks||0):Number(legacyQuote.price_kopecks||0);
  if(deliveryEl){
    if(!cartCount()) deliveryEl.textContent='Доставка: 0 ₽';
    else if(ozonDeliveryRequired() && !state.store.ozonDelivery?.selectedPoint && !state.store.ozonDelivery?.manualPoint) deliveryEl.textContent='Доставка: выберите ПВЗ Ozon.';
    else if(ozonQuoteRequired() && !ozonQuote) deliveryEl.textContent='Доставка Ozon: ожидается расчёт.';
    else if(ozonQuoteRequired()) deliveryEl.textContent=`Доставка Ozon: ${ozonDeliveryQuoteText(ozonQuote)}`;
    else if(ozonDeliveryRequired() && ozonEstimate) deliveryEl.textContent=`Доставка: ${moneyRub(deliveryPrice)} · ${ozonDeliveryEstimateText(ozonEstimate)}`;
    else deliveryEl.textContent=`Доставка: ${moneyRub(deliveryPrice)}`;
  }
  if(!wrap){ restoreCheckoutDraft(); return; }
  setHtml(wrap,'');
  const cart=state.store.cart||[];
  if(cartCard) cartCard.classList.toggle('is-empty', !cart.length);
  reconcileSausageRedemption();
  renderCartRewardsControl();
  if(!cart.length){
    wrap.classList.add('muted');
    const empty=document.createElement('div');
    empty.textContent='Корзина пуста. Добавьте товары из магазина — здесь появятся количество, доставка и итоговая сумма.';
    wrap.appendChild(empty);
    if(total) total.textContent='Итого: 0 ₽';
    renderCartMinimumCounter(false);
    updateCheckoutAuthUi();
    restoreCheckoutDraft();
    return;
  }
  wrap.classList.remove('muted');
  cart.forEach((i)=>{
    const row=document.createElement('div');
    row.className='cart-item';
    const info=document.createElement('div');
    info.className='cart-item__info';
    const unitGrams=resolveCartItemShippingWeightGrams(i);
    const lineGrams=unitGrams*Number(i.quantity||1);
    const originalLine=Number(i.price_kopecks)*Number(i.quantity||1);
    const weightHint=unitGrams>0
      ? `<span class="muted">${escapeHtml(String(unitGrams))} г × ${Number(i.quantity||1)} = ${escapeHtml(String(lineGrams))} г</span>`
      : `<span class="muted">вес не указан</span>`;
    info.innerHTML=`<strong>${escapeHtml(i.title)}</strong>${productPriceHtml(i)}<span>× ${Number(i.quantity||1)}</span>${weightHint}`;
    const actions=document.createElement('div');
    actions.className='cart-item__actions';
    actions.appendChild(buildQuantityControl({
        id:i.product_id,
        title:i.title,
        price_kopecks:i.price_kopecks,
        photo_url:i.photo_url||(catalogProductById(i.product_id)||{}).photo_url,
        weight_grams:unitGrams||Number((catalogProductById(i.product_id)||{}).weight_grams||0)||0,
        weight:String(unitGrams||(catalogProductById(i.product_id)||{}).weight||i.weight||''),
      }, true));
    const sumLine=document.createElement('div');
    sumLine.className='cart-item__sum';
    sumLine.textContent=moneyRub(originalLine);
    row.append(info, actions, sumLine);
    wrap.appendChild(row);
  });
  renderCartTotal(total, cartSubtotal()+deliveryPrice);
  renderCartMinimumCounter(false);
  updateCheckoutAuthUi();
  restoreCheckoutDraft();
}
async function submitCheckout(ev){
  ev.preventDefault();
  if(storeCheckoutSubmitting) return;
  storeCheckoutSubmitting=true;
  // A submit event loses currentTarget after the first asynchronous wait.
  const f=ev.currentTarget;
  const out=document.getElementById('checkoutResult');
  if(out){
    out.setAttribute('role','status');
    out.setAttribute('aria-live','polite');
    out.textContent='';
  }
  try{
    if(!(state.store.cart||[]).length) return showToast('Добавьте хотя бы один товар к заказу.', true);
    const missing=cartMinimumOrderMissingKopecks();
    if(missing>0) {
      renderCartMinimumCounter(true);
      const counterEl = document.getElementById('cartMinimumCounter');
      if (counterEl) {
        counterEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
      return showToast(`Для оформления заказа добавьте товары ещё на ${Math.ceil(missing/100).toLocaleString('ru-RU')} рублей`, true);
    }
    if(!(state.isAuthenticated && Number(state.userId||0)>0)){ openPaywall('auth_required'); return; }
    if(ozonDeliveryRequired()){
      if(!state.store.ozonDelivery?.selectedPoint && !state.store.ozonDelivery?.manualPoint){
        showToast('Выберите пункт выдачи Ozon.',true);
        openOzonPvzDialog();
        return;
      }
      if(ozonQuoteRequired() && !currentOzonQuote()){
        const quoted=await refreshOzonDeliveryQuote();
        if(!quoted) return;
      }
    }
    const fd=new FormData(f);
    const body=Object.fromEntries(fd.entries());
    body.accept_public_offer=Boolean(fd.get('accept_public_offer'));
    body.accept_privacy_policy=Boolean(fd.get('accept_privacy_policy'));
    body.accept_age_18=Boolean(fd.get('accept_age_18'));
    if(!body.accept_public_offer || !body.accept_privacy_policy || !body.accept_age_18){
      return showToast('Отметьте все согласия перед оформлением заказа.', true);
    }
    reconcileSausageRedemption();
    body.items=(state.store.cart||[]).map(i=>({product_id:i.product_id,quantity:i.quantity}));
    body.use_sausages=Boolean(isSausagePromoActive() && state.store.useSausages && cartSausageSpent()>0);
    if(ozonQuoteRequired()) body.expected_ozon_quote=ozonQuoteConfirmationPayload(currentOzonQuote());
    const idempotencyKey=getStoreCheckoutIdempotencyKey(body);
    const res=await api('/api/store/checkout',{method:'POST',body,timeoutMs:90000,headers:{'Idempotency-Key':idempotencyKey}});
    if(res?.pending_reconciliation){
      if(out) out.textContent='Запрос на оплату уже отправлен в банк и проверяется. Повторно оплачивать заказ не нужно.';
      showToast('Запрос на оплату проверяется. Повторно оформлять или оплачивать заказ не нужно.',true);
      return;
    }
    if(!res?.payment_url){
      const message='Ссылка на оплату не получена. Повторно оформлять заказ пока не нужно. Обратитесь в поддержку.';
      if(out) out.textContent=message;
      showToast(message,true);
      return;
    }
    if(out) out.textContent='Заказ создан, переходим к оплате…';
    storeCheckoutNavigating=true;
    if(res.payment_url) window.location.href=res.payment_url;
    // This attempt received a payment URL and initiated navigation. A later
    // checkout after Back is a new order; the old bank invoice is untouched.
    // Hold the submit lock until pageshow so a double click during navigation
    // cannot create another order. A synchronous navigation error retains it.
    clearStoreCheckoutAttempt(idempotencyKey);
  }catch(error){
    storeCheckoutNavigating=false;
    const updatedQuote=error?.details?.reason==='ozon_quote_changed' ? error.details.quote : null;
    if(Number(error?.status)===409 && updatedQuote?.delivery_point){
      clearStoreCheckoutAttempt();
      const ozon=state.store.ozonDelivery||{};
      ozon.quote=updatedQuote;
      ozon.selectedPoint=updatedQuote.delivery_point;
      state.store.ozonDelivery=ozon;
      ozon.quoteFingerprint=currentOzonQuoteFingerprint();
      renderCart();
      if(out) out.textContent='Расчёт Ozon обновился. Проверьте новую сумму и подтвердите заказ повторно.';
      showToast('Расчёт Ozon обновился. Проверьте сумму и нажмите «Оформить заказ» ещё раз.',true);
      return;
    }
    if(error?.details?.reason==='checkout_attempt_finished'){
      clearStoreCheckoutAttempt();
      const message=errorText(error,'Предыдущая попытка оформления уже завершена.')+' Проверьте статус заказа перед новой попыткой.';
      if(out) out.textContent=message;
      showToast(message,true);
      return;
    }
    // Keep the attempt key: a network failure does not prove that the bank
    // rejected the request, and a manual retry must reconcile the same order.
    const message=errorText(error,'Не удалось оформить заказ. Попробуйте ещё раз.');
    if(out) out.textContent=message;
    showToast(message,true);
  }finally{
    if(!storeCheckoutNavigating) storeCheckoutSubmitting=false;
  }
}
function safeStoreBannerUrl(raw,{image=false}={}){
  const value=String(raw||'').trim();
  if(!value||/[\s\\\u0000-\u001f]/.test(value)) return '';
  if(!image&&value.startsWith('#')) return value;
  if(value.startsWith('/')&&!value.startsWith('//')) return value;
  try{ const url=new URL(value); if(url.protocol==='https:'&&!url.username&&!url.password) return url.href; }catch{}
  return '';
}
function renderManagedStoreBanners(banners){
  const viewport=el.storePromoBanner.querySelector('.store-promo-carousel__viewport');
  const dots=el.storePromoBanner.querySelector('.store-promo-carousel__dots');
  if(!viewport||!dots) return;
  viewport.replaceChildren(); dots.replaceChildren();
  banners.forEach((banner)=>{
    if(banner.is_active===false) return;
    const article=document.createElement('article');
    const theme={sausages:'cashback',referral:'referral','review-bonus':'reviews',custom:'custom'}[banner.kind]||'custom';
    article.className=`store-promo-slide store-promo-slide--${theme}`;
    article.dataset.storePromoSlide=String(banner.id||''); article.setAttribute('aria-hidden','true');
    const imageUrl=safeStoreBannerUrl(banner.image_url,{image:true});
    if(imageUrl){
      const img=document.createElement('img'); img.className='store-promo-slide__image'; img.src=imageUrl;
      img.alt=''; img.loading='lazy'; img.addEventListener('error',()=>img.remove()); article.append(img);
    }
    const content=document.createElement('div');
    const title=document.createElement('strong'); title.textContent=String(banner.title||''); content.append(title);
    const body=document.createElement('p'); body.textContent=String(banner.text||''); content.append(body);
    const link=safeStoreBannerUrl(banner.link_url);
    if(link){
      const anchor=document.createElement('a'); anchor.className='store-promo-slide__link'; anchor.href=link;
      anchor.textContent=String(banner.link_text||'Подробнее');
      if(link.startsWith('https://')){ anchor.target='_blank'; anchor.rel='noopener noreferrer'; }
      content.append(anchor);
    }
    article.append(content); viewport.append(article);
    const dot=document.createElement('button'); dot.type='button'; dot.dataset.storePromoDot=String(banner.id||'');
    dot.setAttribute('aria-label',String(banner.title||'Акция')); dots.append(dot);
  });
  const visible=visibleStorePromoSlides().length>0;
  el.storePromoBanner.classList.toggle('hidden',!visible);
  if(!visible){ stopStorePromoCarousel(); return; }
  showStorePromoSlide(Math.min(state.store.promoCarouselIndex,visibleStorePromoSlides().length-1));
  startStorePromoCarousel();
}
function markAdminBannersDirty(){
  state.admin.storeBannersDirty=true;
  const status=document.getElementById('adminStoreBannersStatus');
  if(status) status.textContent='Есть несохранённые изменения. Они появятся на сайте и в приложении после сохранения.';
}
async function loadAdminBanners({discard=false}={}){
  if(state.admin.storeBannersDirty&&!discard) return;
  try{
    state.admin.storeBanners=await api('/api/admin/store/banners');
    state.admin.storeBannersDirty=false; state.admin.storeBannersError='';
  }catch(error){ state.admin.storeBannersError=errorText(error,'Не удалось загрузить баннеры.'); }
  renderAdminBanners();
}
function renderAdminBanners(){
  const container=document.getElementById('adminStoreBannersList'); if(!container) return;
  container.replaceChildren();
  const collection=state.admin.storeBanners;
  const status=document.getElementById('adminStoreBannersStatus');
  if(status) status.textContent=state.admin.storeBannersError||(state.admin.storeBannersDirty?'Есть несохранённые изменения.':collection?'Изменения общие для сайта и приложения.':'Загрузите баннеры для редактирования.');
  const saving=Boolean(state.admin.storeBannersSaving);
  const save=document.getElementById('adminStoreBannersSave'); if(save) save.disabled=saving||!collection;
  const add=document.getElementById('adminStoreBannerAdd'); if(add) add.disabled=saving||!collection||(collection.banners||[]).length>=20;
  if(!collection) return;
  (collection.banners||[]).forEach((banner,index)=>{
    const card=document.createElement('article'); card.className='admin-banner-card';
    card.innerHTML=`<div class="admin-banner-card__heading"><strong>Баннер ${index+1}</strong><span class="admin-banner-card__actions"><button type="button" data-banner-action="up" aria-label="Переместить баннер вверх" ${index===0?'disabled':''}>↑</button><button type="button" data-banner-action="down" aria-label="Переместить баннер вниз" ${index===collection.banners.length-1?'disabled':''}>↓</button><button type="button" data-banner-action="remove" class="danger">Удалить</button></span></div><label class="field checkbox-field"><span>Показывать баннер</span><input type="checkbox" data-banner-field="is_active" ${banner.is_active?'checked':''}></label><label class="field"><span>Условия показа</span><select data-banner-field="kind"><option value="custom">Всегда, когда баннер включён</option><option value="sausages">Когда включена программа колбасок</option><option value="referral">Когда включена акция «Приведи друга»</option><option value="review-bonus">Когда включены колбаски за отзывы</option></select></label><label class="field"><span>Заголовок</span><input data-banner-field="title" maxlength="160" value="${escapeHtml(banner.title)}"></label><label class="field"><span>Текст</span><textarea data-banner-field="text" rows="4" maxlength="1600">${escapeHtml(banner.text)}</textarea></label><label class="field"><span>Изображение — HTTPS-ссылка или путь /media/…</span><input data-banner-field="image_url" maxlength="2048" value="${escapeHtml(banner.image_url)}" placeholder="Можно оставить пустым"></label><label class="field"><span>Ссылка кнопки</span><input data-banner-field="link_url" maxlength="2048" value="${escapeHtml(banner.link_url)}" placeholder="Например, /#account"></label><label class="field"><span>Текст кнопки</span><input data-banner-field="link_text" maxlength="80" value="${escapeHtml(banner.link_text)}" placeholder="Подробнее"></label><div class="admin-banner-preview"><img alt="" hidden><strong></strong><p></p></div>`;
    card.querySelector('[data-banner-field="kind"]').value=banner.kind||'custom';
    const preview=()=>{
      card.querySelector('.admin-banner-preview strong').textContent=banner.title||'Без заголовка';
      card.querySelector('.admin-banner-preview p').textContent=banner.text||'';
      const image=card.querySelector('.admin-banner-preview img');
      const src=safeStoreBannerUrl(banner.image_url,{image:true}); image.hidden=!src;
      if(src) image.src=src; else image.removeAttribute('src');
    };
    card.querySelectorAll('[data-banner-field]').forEach((field)=>{
      field.disabled=saving;
      field.addEventListener('input',()=>{
        banner[field.dataset.bannerField]=field.type==='checkbox'?field.checked:field.value;
        markAdminBannersDirty(); preview();
      });
    });
    card.querySelectorAll('[data-banner-action]').forEach((button)=>{
      button.disabled=button.disabled||saving;
      button.addEventListener('click',()=>{
        const action=button.dataset.bannerAction;
        if(action==='remove') collection.banners.splice(index,1);
        else{
          const next=index+(action==='up'?-1:1);
          if(next<0||next>=collection.banners.length) return;
          [collection.banners[index],collection.banners[next]]=[collection.banners[next],collection.banners[index]];
        }
        collection.banners.forEach((item,order)=>{item.sort_order=order;});
        markAdminBannersDirty(); renderAdminBanners();
      });
    });
    preview(); container.append(card);
  });
}
async function saveAdminBanners(){
  if(state.admin.storeBannersSaving||!state.admin.storeBanners) return;
  state.admin.storeBannersSaving=true; state.admin.storeBannersError=''; renderAdminBanners();
  try{
    const current=state.admin.storeBanners;
    state.admin.storeBanners=await api('/api/admin/store/banners',{method:'PUT',body:{revision:current.revision,banners:current.banners}});
    state.admin.storeBannersDirty=false; showToast('Баннеры сохранены для сайта и приложения.');
    try{ await loadStore(); }catch{ showToast('Баннеры сохранены. Витрина обновится при следующей загрузке.'); }
  }catch(error){
    state.admin.storeBannersError=errorText(error,'Не удалось сохранить баннеры.');
    showToast(state.admin.storeBannersError,true);
  }finally{ state.admin.storeBannersSaving=false; renderAdminBanners(); }
}
function renderAdminPromos(){
  const wrap=document.getElementById('adminStorePromos'); if(!wrap) return;
  const saus=(state.admin.storePromoSettings||{}).sausages||{};
  const reviewBonus=state.admin.reviewBonusSettings||{};
  const referral=state.admin.referralSettings||{};
  const settings=state.admin.storeSettings||{};
  wrap.innerHTML=`<h4>Колбаски</h4><label class="field checkbox-field"><span>Программа колбасок включена</span><input id="adminPromoSausagesActive" type="checkbox" ${Number(saus.is_active||0)?'checked':''}></label><label class="field"><span>Кэшбэк обычным пользователям, %</span><input id="adminPromoStandardPercent" type="number" min="0" max="100" step="1" value="${Number(saus.standard_percent??5)}"></label><label class="field"><span>Кэшбэк с оплаченной PREMIUM, %</span><input id="adminPromoPremiumPercent" type="number" min="0" max="100" step="1" value="${Number(saus.premium_percent??10)}"></label><p class="muted">Триал относится к обычным пользователям. Доставка в кэшбэк не входит.</p><hr><h4>Колбаски за отзывы</h4><label class="field checkbox-field"><span>Акция за отзывы включена</span><input id="adminReviewBonusActive" type="checkbox" ${reviewBonus.is_active?'checked':''}></label><label class="field"><span>Колбасок за содержательный отзыв</span><input id="adminReviewBonusSausages" type="number" min="0" max="1000000" step="1" value="${Number(reviewBonus.sausages??20)}"></label><p class="muted">Минимум ${Number(reviewBonus.minimum_characters||100)} содержательных символов. Пробелы, невидимые символы и однообразный набор не учитываются. За один товар бонус начисляется один раз. Число автоматически показывается в баннере.</p><hr><h4>Приведи друга</h4><label class="field checkbox-field"><span>Реферальная акция включена</span><input id="adminReferralActive" type="checkbox" ${referral.is_active?'checked':''}></label><label class="field"><span>Начисление с первой покупки друга, %</span><input id="adminReferralPercent" type="number" min="0" max="100" step="1" value="${Number(referral.percent??5)}"></label><p class="muted">Процент считается от оплаченной стоимости товаров без доставки. Лимит — 2 000 колбасок за одного друга. Этот же процент показывается в баннере.</p><button type="button" id="adminPromoSaveBtn">Сохранить акции</button><hr><h4>Настройки магазина</h4><label class="field"><span>Минимальная сумма заказа, ₽</span><input id="adminStoreMinimumOrderRub" type="number" min="0" step="1" inputmode="numeric" autocomplete="off" value="${Number(settings.minimum_order_rub||0)}"></label><p class="muted">Считается по товарам без доставки и после списания колбасок.</p><button type="button" id="adminStoreSettingsSaveBtn">Сохранить настройки магазина</button>`;
  wrap.querySelector('#adminPromoSaveBtn')?.addEventListener('click', saveAdminPromos);
  wrap.querySelector('#adminStoreSettingsSaveBtn')?.addEventListener('click', saveAdminStoreSettings);
  renderAdminBanners();
}
async function saveAdminPromos(){
  const reviewBonusSausages=Math.trunc(Number(document.getElementById('adminReviewBonusSausages')?.value||0));
  if(!Number.isFinite(reviewBonusSausages)||reviewBonusSausages<0||reviewBonusSausages>1000000) return showToast('Количество колбасок за отзыв должно быть от 0 до 1 000 000.',true);
  if(document.getElementById('adminReviewBonusActive')?.checked&&reviewBonusSausages<=0) return showToast('Для включённой акции за отзывы укажите хотя бы 1 колбаску.',true);
  const body={sausages:{is_active:document.getElementById('adminPromoSausagesActive')?.checked, standard_percent:document.getElementById('adminPromoStandardPercent')?.value, premium_percent:document.getElementById('adminPromoPremiumPercent')?.value},review_bonus:{is_active:document.getElementById('adminReviewBonusActive')?.checked,sausages:reviewBonusSausages},referral:{is_active:document.getElementById('adminReferralActive')?.checked,percent:document.getElementById('adminReferralPercent')?.value}};
  const res=await api('/api/admin/store/promos',{method:'PATCH',body}); state.admin.storePromoSettings=res.store_promo_settings||{}; state.store.promoSettings=res.store_promo_settings||state.store.promoSettings||{}; state.admin.reviewBonusSettings=res.review_bonus_settings||{}; state.store.reviewBonusSettings=res.review_bonus_settings||state.store.reviewBonusSettings||{}; state.admin.referralSettings=res.referral_settings||{}; state.store.referralSettings=res.referral_settings||state.store.referralSettings||{}; renderAdminPromos(); renderCart(); await loadStore(); showToast('Акции сохранены.');
}
async function saveAdminStoreSettings(){
  const body={minimum_order_rub:document.getElementById('adminStoreMinimumOrderRub')?.value||0};
  const res=await api('/api/admin/store/settings',{method:'PATCH',body});
  state.admin.storeSettings=res.store_settings||{};
  state.store.storeSettings=res.store_settings||state.store.storeSettings||{};
  renderAdminPromos(); renderCart(); showToast('Настройки магазина сохранены.');
}

function renderAdminStore(){ const grid=document.getElementById('adminStoreGrid'); if(!grid) return; setHtml(grid,''); (state.admin.storeProducts||[]).forEach(p=>{ const c=document.createElement('article'); c.className='equipment-card store-product-card'; c.draggable=true; c.dataset.id=p.id; if(p.photo_url){const img=document.createElement('img'); img.src=p.photo_url; img.alt=p.title; c.appendChild(img);} c.innerHTML+=`<h3>${escapeHtml(p.title)}</h3><p class="store-product-card__meta">${escapeHtml(p.category_name||'Магазин')}</p>${productPriceHtml(p)}${isStoreProductDigital(p)?`<p class="admin-book-publication">${Number(p.is_active)===1&&adminBookHasSource(p.book_config||{})?'Книга опубликована':'Книга · черновик'}</p>`:''}`; c.onclick=()=>fillAdminStore(p); c.ondragstart=e=>e.dataTransfer.setData('text/plain',p.id); c.ondragover=e=>e.preventDefault(); c.ondrop=async e=>{e.preventDefault(); const from=e.dataTransfer.getData('text/plain'); const ids=Array.from(grid.querySelectorAll('[data-id]')).map(x=>x.dataset.id); const to=ids.indexOf(String(p.id)); const fi=ids.indexOf(String(from)); if(fi>=0&&to>=0){ids.splice(to,0,ids.splice(fi,1)[0]); await api('/api/admin/store/products/reorder',{method:'POST',body:{product_ids:ids}}); await refreshAdminStore();}}; grid.appendChild(c); }); renderAdminDelivery(); renderAdminStoreCategories();
  const productId=String(document.getElementById('adminStoreProductId')?.value||'').trim();
  const currentProduct=(state.admin.storeProducts||[]).find((p)=>String(p.id)===productId);
  if(currentProduct) fillAdminProductCategoryOptions(currentProduct.category_id||'', currentProduct.category_name||'');
  else fillAdminProductCategoryOptions(String(document.getElementById('adminStoreCategoryName')?.value||'').trim());
  renderAdminStoreRelated();
  renderAdminStoreVariants();
}
function renderAdminStoreVariants(){
  const input=document.getElementById('adminStoreVariantGroup');
  const options=document.getElementById('adminStoreVariantGroups');
  const members=document.getElementById('adminStoreVariantMembers');
  if(!input) return;
  const products=state.admin.storeProducts||[];
  const group=String(input.value||'').trim();
  if(options){
    setHtml(options,'');
    [...new Set(products.map(storeVariantGroup).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'ru')).forEach(title=>{
      const option=document.createElement('option'); option.value=title; options.appendChild(option);
    });
  }
  if(!members) return;
  setHtml(members,'');
  if(!group) return;
  const currentId=Number(document.getElementById('adminStoreProductId')?.value||0);
  products.filter(product=>storeVariantGroup(product)===group).forEach(product=>{
    const button=document.createElement('button'); button.type='button'; button.className='chip';
    button.textContent=`${storeVariantLabel(product)} · ${product.price_text||moneyRub(product.price_kopecks||0)}${Number(product.is_active??1)===1?'':' · скрыт'}`;
    button.disabled=Number(product.id)===currentId;
    button.onclick=()=>fillAdminStore(product);
    members.appendChild(button);
  });
}
function normalizeAdminRelatedIds(raw){
  const seen=new Set();
  const out=[];
  (Array.isArray(raw)?raw:[]).forEach((value)=>{
    const id=Number(value||0);
    if(!id || seen.has(id)) return;
    seen.add(id);
    out.push(id);
  });
  return out;
}
function renderAdminStoreRelated(){
  const list=document.getElementById('adminStoreRelatedList');
  const select=document.getElementById('adminStoreRelatedSelect');
  const currentId=Number(document.getElementById('adminStoreProductId')?.value||0);
  const ids=normalizeAdminRelatedIds(state.admin.relatedProductIds);
  state.admin.relatedProductIds=ids;
  if(select){
    setHtml(select,'');
    const placeholder=document.createElement('option');
    placeholder.value='';
    placeholder.textContent='Выберите товар…';
    select.appendChild(placeholder);
    (state.admin.storeProducts||[])
      .filter((p)=>Number(p.id)!==currentId && !ids.includes(Number(p.id)))
      .forEach((p)=>{
        const opt=document.createElement('option');
        opt.value=String(p.id);
        opt.textContent=p.title||`Товар #${p.id}`;
        select.appendChild(opt);
      });
  }
  if(!list) return;
  if(!ids.length){
    list.className='admin-store-related-list muted';
    list.textContent='Пока ничего не привязано.';
    return;
  }
  list.className='admin-store-related-list';
  setHtml(list,'');
  ids.forEach((id)=>{
    const product=(state.admin.storeProducts||[]).find((p)=>Number(p.id)===id);
    const row=document.createElement('div');
    row.className='admin-store-related-item';
    const title=document.createElement('span');
    title.textContent=product?.title||`Товар #${id}`;
    const btn=document.createElement('button');
    btn.type='button';
    btn.className='chip';
    btn.textContent='Убрать';
    btn.onclick=()=>{
      state.admin.relatedProductIds=normalizeAdminRelatedIds(state.admin.relatedProductIds).filter((x)=>x!==id);
      renderAdminStoreRelated();
    };
    row.append(title, btn);
    list.appendChild(row);
  });
}
function addAdminStoreRelated(){
  const id=Number(document.getElementById('adminStoreRelatedSelect')?.value||0);
  if(!id) return showToast('Выберите товар.', true);
  const currentId=Number(document.getElementById('adminStoreProductId')?.value||0);
  if(currentId && id===currentId) return showToast('Нельзя привязать товар к самому себе.', true);
  const ids=normalizeAdminRelatedIds(state.admin.relatedProductIds);
  if(ids.includes(id)) return;
  if(ids.length>=24) return showToast('Максимум 24 связанных товара.', true);
  ids.push(id);
  state.admin.relatedProductIds=ids;
  renderAdminStoreRelated();
}
function fillAdminProductCategoryOptions(selectedId='', selectedName=''){
  const sel=document.getElementById('adminStoreCategoryName');
  if(!sel) return;
  const current=String(selectedId || sel.value || '').trim();
  const cats=state.admin.storeCategories||[];
  setHtml(sel,'');
  if(!cats.length){
    const empty=document.createElement('option');
    empty.value='';
    empty.textContent=selectedName || 'Нет категорий — создайте ниже';
    empty.dataset.title=selectedName || 'Магазин';
    sel.appendChild(empty);
    return;
  }
  cats.forEach((cat)=>{
    const opt=document.createElement('option');
    opt.value=String(cat.id||'');
    opt.dataset.title=String(cat.title||'');
    opt.textContent=`${cat.title||cat.id} (${Number(cat.products_count||0)})`;
    if(String(cat.id||'')===current) opt.selected=true;
    sel.appendChild(opt);
  });
  if(current && !cats.some((c)=>String(c.id)===current)){
    const opt=document.createElement('option');
    opt.value=current;
    opt.dataset.title=selectedName || current;
    opt.textContent=selectedName || current;
    opt.selected=true;
    sel.appendChild(opt);
  }
}
function fillAdminStore(p={}){
  document.getElementById('adminStoreProductId').value=p.id||'';
  fillAdminProductCategoryOptions(p.category_id||'', p.category_name||'Магазин');
  document.getElementById('adminStoreTitle').value=p.title||'';
  const variantGroup=document.getElementById('adminStoreVariantGroup');
  if(variantGroup) variantGroup.value=storeVariantGroup(p);
  const variantLabel=document.getElementById('adminStoreVariantLabel');
  if(variantLabel) variantLabel.value=p.variant_label||'';
  document.getElementById('adminStoreDescription').value=p.description||'';
  document.getElementById('adminStorePrice').value=p.price_rub||0;
  document.getElementById('adminStoreOldPrice').value=p.old_price_rub||0;
  const weightInput=document.getElementById('adminStoreWeight');
  if(weightInput){
    const grams=Number(p.weight_grams);
    weightInput.value=Number.isFinite(grams) && grams>=0 ? String(Math.round(grams)) : String(p.weight||'').trim();
  }
  const lengthInput=document.getElementById('adminStoreLength');
  if(lengthInput){
    lengthInput.value=String(p.length||'').trim();
  }
  const dimensionsInput=document.getElementById('adminStoreDimensions');
  if(dimensionsInput) dimensionsInput.value=String(p.dimensions||'').trim();
  document.getElementById('adminStorePhotoPath').value=p.photo_path||'';
  document.getElementById('adminStoreSourceUrl').value=p.source_url||'';
  document.getElementById('adminStoreStock').value=p.stock||0;
  document.getElementById('adminStoreActive').checked=Number(p.is_active??1)===1;
  fillAdminBookEditor(p);
  state.admin.relatedProductIds=normalizeAdminRelatedIds(p.related_ids);
  renderAdminStoreRelated();
  renderAdminStoreVariants();
}
function renderAdminDelivery(){
  const wrap=document.getElementById('adminDeliveryList');
  if(!wrap) return;
  setHtml(wrap,'');
  (state.admin.storeDeliveryMethods||[]).forEach((d)=>{
    const row=document.createElement('div');
    row.className='ingredient-row';
    row.innerHTML=`<span>${escapeHtml(d.title)}</span><label><input type="checkbox" ${d.is_active?'checked':''}> активно</label><button type="button">Сохранить</button>`;
    row.querySelector('button').onclick=async()=>{
      await api(`/api/admin/store/delivery/${encodeURIComponent(d.code)}`,{
        method:'PATCH',
        body:{ is_active:row.querySelector('input[type=checkbox]').checked },
      });
      await refreshAdminStore();
    };
    wrap.appendChild(row);
  });
  renderAdminDeliverySizes();
}
function renderAdminDeliverySizes(){
  const wrap=document.getElementById('adminDeliverySizesList');
  if(!wrap) return;
  setHtml(wrap,'');
  const sizes=state.admin.storeDeliverySizes||[];
  if(!sizes.length){
    wrap.classList.add('muted');
    wrap.textContent='Тарифы посылок ещё не созданы. Перезапустите сервер (init_db).';
    return;
  }
  wrap.classList.remove('muted');
  sizes.forEach((size)=>{
    const row=document.createElement('div');
    row.className='ingredient-row';
    const maxKg=(Number(size.max_weight_grams||0)/1000).toLocaleString('ru-RU');
    row.innerHTML=`
      <span><strong>${escapeHtml(size.title||size.code)}</strong> · до ${escapeHtml(maxKg)} кг</span>
      <input type="number" min="0" step="0.01" value="${Number(size.price_rub||0)}" title="Цена, ₽" autocomplete="off" inputmode="decimal">
      <label><input type="checkbox" ${Number(size.is_active??1)===1?'checked':''}> активно</label>
      <button type="button">Сохранить</button>
    `;
    row.querySelector('button').onclick=async()=>{
      try{
        await api(`/api/admin/store/delivery-sizes/${encodeURIComponent(size.code)}`,{
          method:'PATCH',
          body:{
            price: row.querySelector('input[type=number]').value,
            is_active: row.querySelector('input[type=checkbox]').checked,
          },
        });
        setResult(document.getElementById('adminStoreResult'), `Тариф ${size.title||size.code} сохранён.`);
        await refreshAdminStore();
        await loadStore();
      }catch(error){
        showToast(errorText(error,'Не удалось сохранить тариф.'), true);
      }
    };
    wrap.appendChild(row);
  });
}
async function refreshAdminStore(){
  const data=await api('/api/store?include_inactive=1');
  state.admin.storeProducts=data.products||[];
  state.admin.storeDeliveryMethods=data.delivery_methods||[];
  state.admin.storeDeliverySizes=data.delivery_sizes||[];
  state.admin.storePromoSettings=data.promo_settings||data.store_promo_settings||{};
  state.admin.reviewBonusSettings=data.review_bonus_settings||{};
  state.admin.referralSettings=data.referral_settings||{};
  state.admin.storeSettings=data.store_settings||{};
  state.store.promoSettings=data.promo_settings||data.store_promo_settings||state.store.promoSettings||{};
  state.store.reviewBonusSettings=data.review_bonus_settings||state.store.reviewBonusSettings||{};
  state.store.referralSettings=data.referral_settings||state.store.referralSettings||{};
  state.store.storeSettings=data.store_settings||state.store.storeSettings||{};
  state.admin.storeCategories=data.categories||[];
  state.store.deliverySizes=data.delivery_sizes||state.store.deliverySizes||[];
  renderAdminStore();
  renderAdminPromos();
  await loadAdminBanners();
  refreshRecipeProductSelectOptions();
}
function getSelectedAdminStoreCategory(){
  const id=String(state.admin.selectedStoreCategoryId||'').trim();
  if(!id) return null;
  return (state.admin.storeCategories||[]).find((c)=>String(c.id)===id) || null;
}
function renderAdminStoreCategories(){
  const select=document.getElementById('adminStoreCategorySelect');
  const editor=document.getElementById('adminStoreCategoryEditor');
  const titleInput=document.getElementById('adminStoreCategoryEditTitle');
  const sortInput=document.getElementById('adminStoreCategoryEditSort');
  const meta=document.getElementById('adminStoreCategoryMeta');
  const reassignWrap=document.getElementById('adminStoreCategoryReassignWrap');
  const reassignSelect=document.getElementById('adminStoreCategoryReassignSelect');
  if(!select) return;
  const cats=state.admin.storeCategories||[];
  const prev=String(state.admin.selectedStoreCategoryId||select.value||'').trim();
  setHtml(select,'');
  const placeholder=document.createElement('option');
  placeholder.value='';
  placeholder.textContent=cats.length ? 'Выберите категорию…' : 'Категорий пока нет';
  select.appendChild(placeholder);
  cats.forEach((cat)=>{
    const opt=document.createElement('option');
    opt.value=String(cat.id||'');
    opt.textContent=`${cat.title||cat.id} · ${Number(cat.products_count||0)} тов.`;
    select.appendChild(opt);
  });
  if(prev && cats.some((c)=>String(c.id)===prev)) select.value=prev;
  else { select.value=''; state.admin.selectedStoreCategoryId=''; }
  const selected=getSelectedAdminStoreCategory();
  if(!selected){
    editor?.classList.add('hidden');
    return;
  }
  editor?.classList.remove('hidden');
  if(titleInput) titleInput.value=selected.title||'';
  if(sortInput) sortInput.value=String(selected.sort_order ?? 1000000);
  if(meta) meta.textContent=`ID: ${selected.id} · товаров: ${Number(selected.products_count||0)}`;
  const others=cats.filter((c)=>String(c.id)!==String(selected.id));
  if(reassignSelect){
    setHtml(reassignSelect,'');
    others.forEach((cat)=>{
      const opt=document.createElement('option');
      opt.value=String(cat.id||'');
      opt.textContent=cat.title||cat.id;
      reassignSelect.appendChild(opt);
    });
  }
  if(Number(selected.products_count||0)>0 && others.length){
    reassignWrap?.classList.remove('hidden');
  } else {
    reassignWrap?.classList.add('hidden');
  }
}
function onAdminStoreCategorySelectChange(){
  state.admin.selectedStoreCategoryId=String(document.getElementById('adminStoreCategorySelect')?.value||'');
  renderAdminStoreCategories();
}
async function saveSelectedAdminStoreCategory(){
  const selected=getSelectedAdminStoreCategory();
  if(!selected) return showToast('Сначала выберите категорию.', true);
  const title=String(document.getElementById('adminStoreCategoryEditTitle')?.value||'').trim();
  const sort_order=Number(document.getElementById('adminStoreCategoryEditSort')?.value||0);
  if(!title) return showToast('Укажите название категории.', true);
  try{
    await api(`/api/admin/store/categories/${encodeURIComponent(selected.id)}`,{method:'PATCH',body:{title,sort_order}});
    setResult(document.getElementById('adminStoreResult'), `Категория «${title}» сохранена.`);
    await refreshAdminStore();
    await loadStore();
  }catch(error){
    showToast(errorText(error,'Не удалось сохранить категорию.'), true);
  }
}
async function deleteSelectedAdminStoreCategory(){
  const selected=getSelectedAdminStoreCategory();
  if(!selected) return showToast('Сначала выберите категорию.', true);
  const count=Number(selected.products_count||0);
  let reassign_to='';
  if(count>0){
    const others=(state.admin.storeCategories||[]).filter((c)=>String(c.id)!==String(selected.id));
    if(!others.length){
      showToast('Нельзя удалить единственную категорию с товарами. Сначала создайте другую.', true);
      return;
    }
    reassign_to=String(document.getElementById('adminStoreCategoryReassignSelect')?.value||'').trim();
    if(!reassign_to) return showToast('Выберите категорию для переноса товаров.', true);
    if(!window.confirm(`Удалить «${selected.title}» и перенести ${count} товар(ов)?`)) return;
  } else if(!window.confirm(`Удалить категорию «${selected.title||selected.id}»?`)) {
    return;
  }
  try{
    await api(`/api/admin/store/categories/${encodeURIComponent(selected.id)}`,{method:'DELETE',body:{reassign_to}});
    state.admin.selectedStoreCategoryId='';
    setResult(document.getElementById('adminStoreResult'), `Категория «${selected.title||selected.id}» удалена.`);
    await refreshAdminStore();
    await loadStore();
  }catch(error){
    showToast(errorText(error,'Не удалось удалить категорию.'), true);
  }
}
async function addAdminStoreCategory(){
  const title=String(document.getElementById('adminStoreCategoryNewTitle')?.value||'').trim();
  const sortRaw=String(document.getElementById('adminStoreCategoryNewSort')?.value||'').trim();
  if(!title) return showToast('Укажите название категории.', true);
  const body={title};
  if(sortRaw!=='') body.sort_order=Number(sortRaw);
  try{
    const res=await api('/api/admin/store/categories',{method:'POST',body});
    const createdId=String(res?.category?.id||'');
    const titleInput=document.getElementById('adminStoreCategoryNewTitle');
    const sortInput=document.getElementById('adminStoreCategoryNewSort');
    if(titleInput) titleInput.value='';
    if(sortInput) sortInput.value='';
    if(createdId) state.admin.selectedStoreCategoryId=createdId;
    setResult(document.getElementById('adminStoreResult'), `Категория «${title}» создана.`);
    await refreshAdminStore();
    await loadStore();
  }catch(error){
    showToast(errorText(error,'Не удалось создать категорию.'), true);
  }
}
async function saveAdminStore(){
  const id=document.getElementById('adminStoreProductId').value;
  const editorGeneration=Number(state.admin.storeEditorGeneration||0);
  const catSel=document.getElementById('adminStoreCategoryName');
  const category_id=String(catSel?.value||'').trim();
  if(!category_id){
    showToast('Выберите категорию товара.', true);
    return;
  }
  const fromState=(state.admin.storeCategories||[]).find((c)=>String(c.id)===category_id);
  const category_name=String(
    fromState?.title
    || catSel?.selectedOptions?.[0]?.dataset?.title
    || 'Магазин'
  ).trim();
  const weightRaw=String(document.getElementById('adminStoreWeight')?.value||'').trim().replace(',', '.');
  const weightGrams=Math.max(0, Math.round(Number(weightRaw||0)||0));
  const lengthRaw=String(document.getElementById('adminStoreLength')?.value||'').trim().replace(',', '.');
  const lengthValue=Number(lengthRaw||0);
  const length=Number.isFinite(lengthValue) && lengthValue>0 ? String(lengthValue) : '';
  const productType=document.getElementById('adminStoreProductType')?.value||'physical';
  const bookConfig=productType==='digital_book'?collectAdminBookConfig():null;
  if(bookConfig&&!validateAdminBookTitle(bookConfig)) return;
  const body={
    category_id,
    category_name,
    title:document.getElementById('adminStoreTitle').value,
    product_type:productType,
    variant_group:productType==='physical'?String(document.getElementById('adminStoreVariantGroup')?.value||'').trim():'',
    variant_label:productType==='physical'?String(document.getElementById('adminStoreVariantLabel')?.value||'').trim():'',
    ...(bookConfig?{book_config:bookConfig}:{}),
    description:document.getElementById('adminStoreDescription').value,
    price:document.getElementById('adminStorePrice').value,
    old_price:document.getElementById('adminStoreOldPrice').value,
    weight: String(weightGrams),
    weight_grams: weightGrams,
    length,
    dimensions:String(document.getElementById('adminStoreDimensions')?.value||'').trim(),
    photo_path:document.getElementById('adminStorePhotoPath').value,
    source_url:document.getElementById('adminStoreSourceUrl').value,
    stock:document.getElementById('adminStoreStock').value,
    is_active:document.getElementById('adminStoreActive').checked && (productType!=='digital_book'||adminBookHasSource()),
    related_ids: normalizeAdminRelatedIds(state.admin.relatedProductIds),
  };
  try{
    const saved=await api(id?`/api/admin/store/products/${id}`:'/api/admin/store/products',{method:id?'PATCH':'POST',body});
    const sameEditor=editorGeneration===Number(state.admin.storeEditorGeneration||0)
      && String(document.getElementById('adminStoreProductId')?.value||'')===String(id||'');
    if(saved?.id&&sameEditor){
      document.getElementById('adminStoreProductId').value=saved.id;
      state.admin.relatedProductIds=normalizeAdminRelatedIds(saved.related_ids);
      if(saved.book_config) state.admin.digitalBook=saved.book_config;
      renderAdminDigitalBookStatus();
    }
    if(sameEditor) setResult(document.getElementById('adminStoreResult'), id ? 'Товар сохранён.' : 'Товар создан.');
    await refreshAdminStore();
    await loadStore();
    renderAdminStoreRelated();
    return saved;
  }catch(error){
    showToast(errorText(error, 'Не удалось сохранить товар.'), true);
  }
}
async function importAdminStore(){
  const file = document.getElementById('adminStoreImportFile')?.files?.[0];
  if (!file) return showToast('Выберите файл импорта.', true);
  const token = String(document.getElementById('adminStoreVkToken')?.value || '').trim();
  const fd = new FormData();
  fd.append('file', file);
  if (token) fd.append('market_token', token);
  const res = await api('/api/admin/store/import', {method: 'POST', body: fd, isFormData: true});
  const vk = res.result?.vk_descriptions || {};
  let syncNote = 'без синхронизации VK (только файл)';
  if (res.result?.vk_sync) {
    syncNote = vk.error
      ? `VK sync ошибка: ${vk.error}`
      : `описания VK: ${vk.enriched || 0}`;
  }
  document.getElementById('adminStoreResult').textContent =
    `Импорт: создано ${res.result.created}, обновлено ${res.result.updated}; ${syncNote}`;
  if (vk.error) showToast(String(vk.error), true);
  await refreshAdminStore();
  await loadStore();
}
async function refreshAdminStoreDescriptions(){
  const token = String(document.getElementById('adminStoreVkToken')?.value || '').trim();
  if (!token) return showToast('Укажите VK Market token.', true);
  setResult(document.getElementById('adminStoreResult'), 'Подтягиваем полные описания из VK…');
  const res = await api('/api/admin/store/descriptions/refresh', {
    method: 'POST',
    body: {market_token: token},
  });
  const vk = res.result?.vk_descriptions || {};
  if (vk.error) {
    document.getElementById('adminStoreResult').textContent = `VK sync ошибка: ${vk.error}`;
    showToast(String(vk.error), true);
    return;
  }
  document.getElementById('adminStoreResult').textContent =
    `Описания обновлены: ${res.result?.updated || 0} (проверено ${vk.checked || 0}, обогащено ${vk.enriched || 0})`;
  await refreshAdminStore();
  await loadStore();
}

async function loadFaq() {
  const list = await api("/api/faq");
  state.faq = list;
  setHtml(el.faqList, "");

  if (!Array.isArray(list) || list.length === 0) {
    const p = document.createElement("p");
    p.className = "muted";
    p.textContent = "FAQ пока пуст.";
    el.faqList.appendChild(p);
    return;
  }

  list.forEach((item) => {
    const wrap = document.createElement("article");
    wrap.className = "faq-item";

    const q = document.createElement("button");
    q.type = "button";
    q.className = "faq-question";
    q.textContent = item.question;
    q.addEventListener("click", () => {
      wrap.classList.toggle("is-open");
    });

    const a = document.createElement("div");
    a.className = "faq-answer";
    a.textContent = item.answer;

    wrap.append(q, a);
    el.faqList.appendChild(wrap);
  });
}

function renderMyTickets(items) {
  setHtml(el.myTickets, "");
  if (!Array.isArray(items) || items.length === 0) {
    el.myTickets.classList.add("muted");
    el.myTickets.textContent = "Обращений пока нет.";
    return;
  }

  el.myTickets.classList.remove("muted");
  items.forEach((ticket) => {
    const status = ticket.is_answered ? "Ответ получен" : "Ожидает ответа";
    const userBadges = `${ticket.is_admin ? '<span class="article-comment__admin-badge">ADMIN</span>' : ""}${ticket.is_premium ? '<span class="article-comment__premium-badge">PREMIUM</span>' : ""}`;
    const row = document.createElement("article");
    row.className = "ticket-item";
    setHtml(row, `
      <div class="ticket-item__meta">#${ticket.id} • ${escapeHtml(formatMskDateTimeLabel(ticket.created_at, "без даты"))}</div>
      <div class="ticket-item__status">${escapeHtml(status)}</div>
      <div class="ticket-item__author"><strong>Вы</strong>${userBadges}</div>
      <div class="ticket-item__question">${escapeHtml(ticket.question_text || "")}</div>
      ${ticket.answer_text ? `<div class="ticket-item__answer"><strong><span class="article-comment__admin-badge">ADMIN</span> Ответ администратора:</strong><br>${escapeHtml(ticket.answer_text)}</div>` : ""}
    `);
    el.myTickets.appendChild(row);
  });
}

async function loadMyTickets() {
  const items = await api(`/api/support/tickets/my?user_id=${encodeURIComponent(state.userId)}`);
  state.myTickets = items;
  renderMyTickets(items);
}

function setupAdminStoreEditorTabs() {
  const wrap = document.querySelector(".admin-store-editor-tabs");
  if (!wrap) return;
  const buttons = Array.from(wrap.querySelectorAll("[data-store-editor]"));
  const panels = {
    product: document.getElementById("adminStoreEditorProduct"),
    settings: document.getElementById("adminStoreEditorSettings"),
    promos: document.getElementById("adminStoreEditorPromos"),
  };

  buttons.forEach((btn) => {
    btn.addEventListener("click", () => {
      const key = btn.dataset.storeEditor;
      buttons.forEach((b) => {
        const active = b === btn;
        b.classList.toggle("is-active", active);
        b.setAttribute("aria-selected", active ? "true" : "false");
      });
      Object.entries(panels).forEach(([name, panel]) => {
        if (!panel) return;
        const active = name === key;
        panel.classList.toggle("is-active", active);
        panel.hidden = !active;
      });
    });
  });
}

function activateAdminPanel(key, { focus = false } = {}) {
  if (!el.adminTabsWrap || !key) return;
  const buttons = Array.from(el.adminTabsWrap.querySelectorAll("[data-admin-panel]"));
  const activeButton = buttons.find((button) => button.dataset.adminPanel === key);
  const activePanel = document.getElementById(`admin-panel-${key}`);
  if (!activeButton || !activePanel) return;

  buttons.forEach((button) => button.classList.toggle("is-active", button === activeButton));
  el.adminPanels.forEach((panel) => panel.classList.toggle("is-active", panel === activePanel));

  if (key === "dashboard") {
    const dashboardAge = Date.now() - Number(state.admin.dashboard.loadedAt || 0);
    if (!state.admin.dashboard.data || dashboardAge > 60_000) {
      void loadAdminDashboard({ silent: Boolean(state.admin.dashboard.data) });
    }
  }
  if (key === "store") { void refreshAdminStore(); }
  if (key === "carts") { void loadAdminCarts(); }
  if (key === "recipes") {
    void ensureAdminStoreProductsLoaded().catch((error) => {
      setResult(el.adminRecipeResult, errorText(error, "Не удалось загрузить товары для привязки."), true);
    });
  }
  if (key === "orders") { void loadAdminOrders(state.admin.ordersStatus || "current"); }
  if (key === "reviews") { void loadAdminReviews(); }
  if (key === "notifications") { void loadAdminNotifications(); }
  if (key === "db") {
    const hasItems = Array.isArray(state.admin.dbLog.items) && state.admin.dbLog.items.length > 0;
    if (!hasItems) {
      void loadAdminDbLog();
    }
    startAdminDbAutoRefresh();
  } else {
    stopAdminDbAutoRefresh();
  }

  if (focus) {
    activeButton.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "nearest" });
    window.requestAnimationFrame(() => activePanel.scrollIntoView({ behavior: "smooth", block: "start" }));
  }
}

function applyRequestedAdminPanel() {
  if (!state.isAdmin) return;
  const panel = String(new URLSearchParams(window.location.search).get("admin_panel") || "").trim();
  if (!panel) return;
  activateTab("admin");
  activateAdminPanel(panel, { focus: true });
}

function setupAdminTabs() {
  if (!el.adminTabsWrap) return;
  el.adminTabsWrap.querySelectorAll("[data-admin-panel]").forEach((button) => {
    button.addEventListener("click", () => activateAdminPanel(button.dataset.adminPanel));
  });
  el.adminSection?.addEventListener("click", (event) => {
    const trigger = event.target.closest("[data-dashboard-target]");
    if (!trigger || !el.adminSection.contains(trigger)) return;
    openAdminDashboardTarget(trigger);
  });
}

function openAdminDashboardTarget(trigger) {
  const target = String(trigger.dataset.dashboardTarget || "");
  if (target === "orders") {
    state.admin.ordersStatus = trigger.dataset.dashboardOrdersStatus || "current";
    if (state.admin.ordersStatus === "digital") {
      state.admin.digitalSalesStatus = trigger.dataset.dashboardDigitalStatus || "delivered";
    }
  }
  activateAdminPanel(target, { focus: true });
}

function dashboardNumber(value) {
  return Math.max(0, Number(value || 0)).toLocaleString("ru-RU");
}

function renderAdminOnlinePresence(data) {
  const values = data || {};
  if (el.adminDashboardOnline && values.users_online !== undefined) {
    el.adminDashboardOnline.textContent = dashboardNumber(values.users_online);
  }
  if (el.adminDashboardOnlineNote && values.online_window_seconds !== undefined) {
    const windowMinutes = Math.max(1, Math.ceil(Number(values.online_window_seconds || 0) / 60));
    el.adminDashboardOnlineNote.textContent = `Уникальные аккаунты · за ${windowMinutes} мин`;
  }
}

function dashboardDayLabel(dateText) {
  const match = String(dateText || "").match(/^\d{4}-(\d{2})-(\d{2})$/);
  return match ? `${match[2]}.${match[1]}` : "—";
}

function dashboardStatusTone(status) {
  const normalized = String(status || "").toLowerCase();
  if (["paid", "payment_pending", "new", "active"].includes(normalized)) return "is-warning";
  if (["ready", "confirmed", "delivered"].includes(normalized)) return "is-success";
  if (["cancelled", "refunded", "payment_failed", "payment_expired", "failed", "revoked"].includes(normalized)) return "is-danger";
  return "";
}

function renderAdminDashboardChart(rows) {
  if (!el.adminDashboardChart) return;
  const daily = Array.isArray(rows) ? rows : [];
  if (!daily.length) {
    setHtml(el.adminDashboardChart, '<div class="admin-dashboard-empty">За выбранный период данных пока нет.</div>');
    return;
  }
  const maximum = Math.max(0, ...daily.map((item) => Number(item.revenue_kopecks || 0)));
  setHtml(el.adminDashboardChart, daily.map((item) => {
    const revenue = Math.max(0, Number(item.revenue_kopecks || 0));
    const transactions = Math.max(0, Number(item.transactions || 0));
    const height = revenue > 0 && maximum > 0 ? Math.max(4, Math.round((revenue / maximum) * 100)) : 0;
    const title = `${dashboardDayLabel(item.date)}: ${moneyRub(revenue)}, операций: ${transactions}`;
    return `
      <div class="admin-dashboard-chart__column" title="${escapeHtml(title)}">
        <div class="admin-dashboard-chart__plot">
          <em class="admin-dashboard-chart__transactions">${escapeHtml(String(transactions))}</em>
          <span class="admin-dashboard-chart__bar${revenue === 0 ? " is-zero" : ""}" style="height:${height}%"></span>
        </div>
        <span class="admin-dashboard-chart__label">${escapeHtml(dashboardDayLabel(item.date))}</span>
      </div>
    `;
  }).join(""));
}

function renderAdminDashboardAttention(attention) {
  if (!el.adminDashboardAttention) return;
  const values = attention || {};
  const items = [
    { key: "orders_waiting", icon: "ЗК", title: "Заказы ждут сборки", note: "Проверьте оплаченные заказы", target: "orders", ordersStatus: "current" },
    { key: "tickets_open", icon: "ПД", title: "Нет ответа в поддержке", note: "Клиенты ожидают обратную связь", target: "support" },
    { key: "low_stock_products", icon: "ОС", title: "Заканчиваются остатки", note: "Пять единиц товара или меньше", target: "store" },
    { key: "digital_sales_attention", icon: "ЦТ", title: "Цифровая выдача", note: "Есть незавершённые отправки", target: "orders", ordersStatus: "digital", digitalStatus: "attention" },
    { key: "draft_articles", icon: "ЧР", title: "Черновики статей", note: "Материалы ещё не опубликованы", target: "articles" },
  ].map((item) => ({ ...item, count: Math.max(0, Number(values[item.key] || 0)) }));
  const total = items.reduce((sum, item) => sum + item.count, 0);
  if (el.adminDashboardAttentionTotal) el.adminDashboardAttentionTotal.textContent = dashboardNumber(total);
  if (total === 0) {
    setHtml(el.adminDashboardAttention, '<div class="admin-dashboard-empty">Всё спокойно — срочных задач нет.</div>');
    return;
  }
  setHtml(el.adminDashboardAttention, items.filter((item) => item.count > 0).map((item) => `
    <button type="button" class="admin-dashboard-attention-item" data-dashboard-target="${escapeHtml(item.target)}" data-dashboard-orders-status="${escapeHtml(item.ordersStatus || "")}" data-dashboard-digital-status="${escapeHtml(item.digitalStatus || "")}">
      <span class="admin-dashboard-attention-item__icon" aria-hidden="true">${escapeHtml(item.icon)}</span>
      <span><b>${escapeHtml(item.title)}</b><small>${escapeHtml(item.note)}</small></span>
      <span class="admin-dashboard-attention-item__count">${dashboardNumber(item.count)}</span>
    </button>
  `).join(""));
}

function renderAdminDashboardOrders(rows) {
  if (!el.adminDashboardRecentOrders) return;
  const orders = Array.isArray(rows) ? rows : [];
  if (!orders.length) {
    setHtml(el.adminDashboardRecentOrders, '<div class="admin-dashboard-empty">Заказов пока нет.</div>');
    return;
  }
  setHtml(el.adminDashboardRecentOrders, orders.map((order) => `
    <div class="admin-dashboard-feed__row">
      <span class="admin-dashboard-feed__title">№ ${escapeHtml(order.order_id || "—")} · ${escapeHtml(order.customer_name || "Без имени")}</span>
      <span class="admin-dashboard-feed__meta">${escapeHtml(formatMskDateTimeLabel(order.created_at, "Без даты"))}</span>
      <span class="admin-dashboard-feed__value">${escapeHtml(moneyRub(order.total_kopecks || 0))}<small class="admin-dashboard-status ${dashboardStatusTone(order.status)}">${escapeHtml(storeOrderStatusText(order.status))}</small></span>
    </div>
  `).join(""));
}

function renderAdminDashboardUsers(rows) {
  if (!el.adminDashboardRecentUsers) return;
  const users = Array.isArray(rows) ? rows : [];
  if (!users.length) {
    setHtml(el.adminDashboardRecentUsers, '<div class="admin-dashboard-empty">Новых пользователей пока нет.</div>');
    return;
  }
  setHtml(el.adminDashboardRecentUsers, users.map((user) => {
    const activeUntil = parseStorageDateTime(user.subscription_active_until);
    const active = String(user.subscription_status || "") === "active" && Boolean(activeUntil && activeUntil > new Date());
    const tier = active ? defaultTierTitle(user.subscription_tier) : "Без подписки";
    return `
      <div class="admin-dashboard-feed__row">
        <span class="admin-dashboard-feed__title">${escapeHtml(user.username || `Пользователь #${user.id}`)}</span>
        <span class="admin-dashboard-feed__meta">${escapeHtml(user.email || formatMskDateTimeLabel(user.created_at, "Email не указан"))}</span>
        <span class="admin-dashboard-feed__value"><small class="admin-dashboard-status ${active ? "is-success" : ""}">${escapeHtml(tier)}</small></span>
      </div>
    `;
  }).join(""));
}

function renderAdminDashboardContent(content) {
  if (!el.adminDashboardContent) return;
  const values = content || {};
  const items = [
    { title: "Статьи", ready: values.articles_published, total: values.articles_total },
    { title: "Рецепты", ready: values.recipes_filled, total: values.recipes_total },
    { title: "Товары", ready: values.products_active, total: values.products_total },
    { title: "Оборудование", ready: values.equipment_active, total: values.equipment_total },
  ];
  setHtml(el.adminDashboardContent, items.map((item) => {
    const ready = Math.max(0, Number(item.ready || 0));
    const total = Math.max(0, Number(item.total || 0));
    const progress = total > 0 ? Math.min(100, Math.round((ready / total) * 100)) : 0;
    return `
      <div class="admin-dashboard-content__row">
        <div class="admin-dashboard-content__labels"><strong>${escapeHtml(item.title)}</strong><strong>${dashboardNumber(ready)} / ${dashboardNumber(total)}</strong></div>
        <div class="admin-dashboard-content__track" aria-label="${escapeHtml(item.title)}: ${progress}%"><span style="width:${progress}%"></span></div>
      </div>
    `;
  }).join(""));
}

function renderAdminDashboard(data) {
  const dashboard = data || {};
  const kpi = dashboard.kpi || {};
  if (el.adminDashboardAdminName) el.adminDashboardAdminName.textContent = state.username || state.userFirstName || "администратор";
  if (el.adminDashboardUpdatedAt) {
    el.adminDashboardUpdatedAt.textContent = `Обновлено ${formatMskDateTimeLabel(dashboard.generated_at, "только что")}`;
  }
  if (el.adminDashboardRevenue) el.adminDashboardRevenue.textContent = moneyRub(kpi.revenue_month_kopecks || 0);
  if (el.adminDashboardRevenueTrend) {
    const change = Number(kpi.revenue_change_percent);
    const hasComparison = kpi.revenue_change_percent !== null && kpi.revenue_change_percent !== undefined;
    el.adminDashboardRevenueTrend.classList.remove("is-positive", "is-negative");
    if (hasComparison) {
      const prefix = change > 0 ? "+" : "";
      el.adminDashboardRevenueTrend.textContent = `${prefix}${change.toLocaleString("ru-RU")}% к тому же периоду`;
      el.adminDashboardRevenueTrend.classList.add(change >= 0 ? "is-positive" : "is-negative");
    } else {
      el.adminDashboardRevenueTrend.textContent = "Нет базы для сравнения";
    }
  }
  if (el.adminDashboardOrders) el.adminDashboardOrders.textContent = dashboardNumber(kpi.orders_waiting);
  if (el.adminDashboardOrdersNote) el.adminDashboardOrdersNote.textContent = `За месяц: ${dashboardNumber(kpi.orders_month)} · Сегодня: ${dashboardNumber(kpi.orders_today)}`;
  if (el.adminDashboardSubscriptions) el.adminDashboardSubscriptions.textContent = dashboardNumber(kpi.active_subscriptions);
  renderAdminOnlinePresence(kpi);
  if (el.adminDashboardUsers) el.adminDashboardUsers.textContent = dashboardNumber(kpi.users_total);
  if (el.adminDashboardUsersNote) el.adminDashboardUsersNote.textContent = `+${dashboardNumber(kpi.users_new_today)} сегодня · +${dashboardNumber(kpi.users_new_month)} за месяц`;
  if (el.adminDashboardTickets) el.adminDashboardTickets.textContent = dashboardNumber(kpi.tickets_open);
  if (el.adminDashboardTicketsNote) {
    el.adminDashboardTicketsNote.textContent = kpi.oldest_ticket_at
      ? `Самое раннее: ${formatMskDateTimeLabel(kpi.oldest_ticket_at, "—")}`
      : "Все обращения обработаны";
  }
  const daily = Array.isArray(dashboard.daily) ? dashboard.daily : [];
  const chartRevenue = daily.reduce((sum, item) => sum + Number(item.revenue_kopecks || 0), 0);
  if (el.adminDashboardChartTotal) el.adminDashboardChartTotal.textContent = moneyRub(chartRevenue);
  const chartStoreRevenue = daily.reduce((sum, item) => sum + Number(item.store_revenue_kopecks || 0), 0);
  const chartSubscriptionRevenue = daily.reduce((sum, item) => sum + Number(item.subscription_revenue_kopecks || 0), 0);
  if (el.adminDashboardStoreRevenue) el.adminDashboardStoreRevenue.textContent = `Магазин: ${moneyRub(chartStoreRevenue)}`;
  if (el.adminDashboardSubscriptionRevenue) el.adminDashboardSubscriptionRevenue.textContent = `Подписки: ${moneyRub(chartSubscriptionRevenue)}`;
  renderAdminDashboardChart(daily);
  renderAdminDashboardAttention(dashboard.attention);
  renderAdminDashboardOrders(dashboard.recent_orders);
  renderAdminDashboardUsers(dashboard.recent_users);
  renderAdminDashboardContent(dashboard.content);
}

async function loadAdminDashboard({ silent = false } = {}) {
  if (!el.adminDashboardChart) return null;
  if (!silent) setButtonBusy(el.adminDashboardRefreshBtn, true, "Обновляем…");
  el.adminDashboardError?.classList.add("hidden");
  try {
    const data = await api("/api/admin/dashboard");
    state.admin.dashboard.data = data;
    state.admin.dashboard.loadedAt = Date.now();
    renderAdminDashboard(data);
    return data;
  } catch (error) {
    if (el.adminDashboardError) {
      el.adminDashboardError.textContent = errorText(error, "Не удалось загрузить дашборд.");
      el.adminDashboardError.classList.remove("hidden");
    }
    if (!state.admin.dashboard.data) {
      if (el.adminDashboardUpdatedAt) el.adminDashboardUpdatedAt.textContent = "Данные временно недоступны";
      if (el.adminDashboardChart) setHtml(el.adminDashboardChart, '<div class="admin-dashboard-empty">Не удалось загрузить данные. Попробуйте обновить.</div>');
    }
    return null;
  } finally {
    if (!silent) setButtonBusy(el.adminDashboardRefreshBtn, false);
  }
}

function renderAdminPlanSettings() {
  if (!el.adminPlanSettings) return;
  const plans = Array.isArray(state.admin.subscriptionPlans) ? state.admin.subscriptionPlans : [];
  setHtml(el.adminPlanSettings, "");
  if (!plans.length) {
    setHtml(el.adminPlanSettings, '<div class="muted">Тарифы не загружены.</div>');
    return;
  }

  plans.forEach((plan) => {
    const row = document.createElement("div");
    row.className = "price-editor-item";
    setHtml(row, `
      <div>
        <label class="field mini">
          <span>Название</span>
          <input type="text" value="${escapeHtml(String(plan.title || plan.tier || '').toUpperCase())}" data-plan-tier="${escapeHtml(plan.tier)}" data-plan-field="title">
        </label>
        <label class="field mini">
          <span>Описание</span>
          <textarea rows="3" data-plan-tier="${escapeHtml(plan.tier)}" data-plan-field="description">${escapeHtml(String(plan.description || ''))}</textarea>
        </label>
        <label class="field mini">
          <span>Пункты описания (по одному на строку)</span>
          <textarea rows="5" data-plan-tier="${escapeHtml(plan.tier)}" data-plan-field="features">${escapeHtml((Array.isArray(plan.features) ? plan.features : []).map((item) => String(item || "").trim()).filter(Boolean).join('\n'))}</textarea>
        </label>
        <div class="muted">Итог: ${Number(plan.final_amount_rub || 0)} ₽</div>
      </div>
      <div class="inline-actions">
        <input type="number" min="1" step="1" value="${Number(plan.base_amount_rub || 1)}" data-plan-tier="${escapeHtml(plan.tier)}" data-plan-field="amount">
        <input type="number" min="0" max="99" step="0.01" value="${Number(plan.discount_percent || 0)}" data-plan-tier="${escapeHtml(plan.tier)}" data-plan-field="discount">
        <button type="button" class="chip" data-plan-tier="${escapeHtml(plan.tier)}">Сохранить</button>
      </div>
    `);
    el.adminPlanSettings.appendChild(row);
  });

  el.adminPlanSettings.querySelectorAll("button[data-plan-tier]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const tier = String(btn.dataset.planTier || "").trim().toLowerCase();
      const amountInput = el.adminPlanSettings.querySelector(`input[data-plan-tier="${tier}"][data-plan-field="amount"]`);
      const discountInput = el.adminPlanSettings.querySelector(`input[data-plan-tier="${tier}"][data-plan-field="discount"]`);
      const titleInput = el.adminPlanSettings.querySelector(`input[data-plan-tier="${tier}"][data-plan-field="title"]`);
      const descInput = el.adminPlanSettings.querySelector(`textarea[data-plan-tier="${tier}"][data-plan-field="description"]`);
      const featuresInput = el.adminPlanSettings.querySelector(`textarea[data-plan-tier="${tier}"][data-plan-field="features"]`);
      const amount = Number(amountInput?.value || 0);
      const discount = Number(discountInput?.value || 0);
      const title = String(titleInput?.value || "").trim();
      const description = String(descInput?.value || "").trim();
      const features = String(featuresInput?.value || "")
        .split(/\r?\n/g)
        .map((line) => line.trim())
        .filter(Boolean);
      setButtonBusy(btn, true, "Сохраняем...");
      try {
        await api(`/api/admin/subscriptions/plans/${encodeURIComponent(tier)}`, {
          method: "PATCH",
          body: {
            user_id: state.userId,
            title,
            description,
            features,
            base_amount_rub: amount,
            discount_percent: discount,
          },
        });
        await refreshAdminBootstrap();
        await refreshPaymentPlansData();
        updateHeader();
        renderHomeAccess();
        renderPaymentPlans();
        showToast(`Тариф ${humanTierTitle(tier)} обновлен.`);
      } catch (error) {
        showToast(errorText(error, "Не удалось сохранить тариф."), true);
      } finally {
        setButtonBusy(btn, false);
      }
    });
  });
}

function renderAdminPromocodes() {
  if (!el.adminPromoList) return;
  const promos = Array.isArray(state.admin.promocodes) ? state.admin.promocodes : [];
  setHtml(el.adminPromoList, "");
  if (!promos.length) {
    el.adminPromoList.textContent = "Промокодов пока нет.";
    el.adminPromoList.classList.add("muted");
    return;
  }
  el.adminPromoList.classList.remove("muted");
  promos.forEach((promo) => {
    const row = document.createElement("article");
    row.className = "ticket-item";
    const status = promo.activated_at ? "Использован" : (promo.active ? "Активен" : "Выключен");
    setHtml(row, `
      <div class="ticket-item__meta">${escapeHtml(String(promo.code || ""))} • ${escapeHtml(String(promo.tier || "").toUpperCase())}</div>
      <div class="ticket-item__status">${status} • скидка ${Number(promo.discount_percent || 0)}% (только 1-й период)</div>
      <div class="inline-actions">
        <button type="button" class="danger mini" data-delete-promo="${escapeHtml(String(promo.code || ""))}">Удалить</button>
      </div>
    `);
    el.adminPromoList.appendChild(row);
  });

  el.adminPromoList.querySelectorAll("button[data-delete-promo]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const code = String(btn.dataset.deletePromo || "").trim();
      if (!code) return;
      if (!window.confirm(`Удалить промокод ${code}?`)) return;
      setButtonBusy(btn, true, "Удаляем...");
      try {
        await api(`/api/admin/promocodes/${encodeURIComponent(code)}`, {
          method: "DELETE",
          body: { user_id: state.userId },
        });
        await refreshAdminBootstrap();
        showToast("Промокод удален.");
      } catch (error) {
        showToast(errorText(error, "Не удалось удалить промокод."), true);
      } finally {
        setButtonBusy(btn, false);
      }
    });
  });
}

function renderRegistrationBonusSettings() {
  const settings = state.admin.registrationBonusSettings || {};
  if (el.adminRegistrationBonusEnabled) {
    el.adminRegistrationBonusEnabled.checked = Boolean(settings.is_active);
  }
  if (el.adminRegistrationBonusAmount) {
    el.adminRegistrationBonusAmount.value = String(Math.max(0, Number(settings.sausages || 0)));
  }
}

async function saveAdminMaintenanceSettings(forcedOverride = null) {
  if (!el.adminMaintenanceSaveBtn) return;
  const forced = typeof forcedOverride === "boolean"
    ? forcedOverride
    : Boolean(el.adminMaintenanceForced?.checked);
  setButtonBusy(el.adminMaintenanceSaveBtn, true, "Сохраняем…");
  if (el.adminMaintenanceForced) el.adminMaintenanceForced.disabled = true;
  try {
    const data = await api("/api/admin/settings/maintenance", {
      method: "PATCH",
      body: { forced },
    });
    state.admin.maintenance = normalizeMaintenanceStatus(data.maintenance || {});
    state.maintenance = state.admin.maintenance;
    renderAdminMaintenanceSettings();
    const message = state.admin.maintenance.forced
      ? "Принудительные техработы включены."
      : state.admin.maintenance.scheduled
        ? "Принудительные техработы выключены, но сейчас действует плановое окно."
        : "Принудительные техработы выключены.";
    setResult(el.adminMaintenanceResult, message);
    showToast(message);
  } catch (error) {
    setResult(el.adminMaintenanceResult, errorText(error, "Не удалось сохранить режим техработ."), true);
  } finally {
    if (el.adminMaintenanceForced) el.adminMaintenanceForced.disabled = false;
    setButtonBusy(el.adminMaintenanceSaveBtn, false);
  }
}

async function saveRegistrationBonusSettings() {
  if (!el.adminRegistrationBonusSaveBtn) return;
  const sausages = Math.trunc(Number(el.adminRegistrationBonusAmount?.value || 0));
  if (!Number.isFinite(sausages) || sausages < 0 || sausages > 1000000) {
    showToast("Количество колбасок должно быть от 0 до 1 000 000.", true);
    return;
  }
  if (el.adminRegistrationBonusEnabled?.checked && sausages <= 0) {
    showToast("Для включённого бонуса укажите хотя бы 1 колбаску.", true);
    return;
  }
  setButtonBusy(el.adminRegistrationBonusSaveBtn, true, "Сохраняем…");
  try {
    const data = await api("/api/admin/settings/registration-bonus", {
      method: "PATCH",
      body: {
        is_active: Boolean(el.adminRegistrationBonusEnabled?.checked),
        sausages,
      },
    });
    state.admin.registrationBonusSettings = data.registration_bonus_settings || {};
    renderRegistrationBonusSettings();
    setResult(el.adminRegistrationBonusResult, "Настройки бонуса сохранены.");
    showToast("Бонус за регистрацию сохранён.");
  } catch (error) {
    setResult(el.adminRegistrationBonusResult, errorText(error, "Не удалось сохранить бонус."), true);
  } finally {
    setButtonBusy(el.adminRegistrationBonusSaveBtn, false);
  }
}

function renderAdminSiteSocialLinks() {
  const links = normalizeSiteSocialLinks(state.admin.siteSocialLinks);
  el.adminSocialLinkInputs.forEach((input) => {
    const key = String(input.dataset.socialInput || "");
    input.value = links[key] || "";
  });
}

async function saveAdminSiteSocialLinks() {
  if (!el.adminSocialLinksSaveBtn) return;
  const links = {};
  for (const input of el.adminSocialLinkInputs) {
    const key = String(input.dataset.socialInput || "");
    if (!SITE_SOCIAL_LINK_KEYS.includes(key)) continue;
    const value = String(input.value || "").trim();
    if (value && !safePublicSocialUrl(value)) {
      input.focus();
      input.reportValidity?.();
      setResult(el.adminSocialLinksResult, "Укажите полную ссылку с http:// или https://.", true);
      return;
    }
    links[key] = value;
  }

  setButtonBusy(el.adminSocialLinksSaveBtn, true, "Сохраняем…");
  try {
    const data = await api("/api/admin/settings/social-links", {
      method: "PATCH",
      body: { site_social_links: links },
    });
    state.admin.siteSocialLinks = normalizeSiteSocialLinks(data.site_social_links);
    state.siteSocialLinks = { ...state.admin.siteSocialLinks };
    renderAdminSiteSocialLinks();
    renderHeroSocialLinks(state.siteSocialLinks);
    setResult(el.adminSocialLinksResult, "Ссылки сохранены.");
    showToast("Ссылки на социальные сети сохранены.");
  } catch (error) {
    setResult(el.adminSocialLinksResult, errorText(error, "Не удалось сохранить ссылки."), true);
  } finally {
    setButtonBusy(el.adminSocialLinksSaveBtn, false);
  }
}

function setAdminNotificationTab(scope) {
  const target = scope === "personal" ? "personal" : "global";
  el.adminNotificationGlobalTab?.classList.toggle("is-active", target === "global");
  el.adminNotificationPersonalTab?.classList.toggle("is-active", target === "personal");
  el.adminNotificationGlobalPanel?.classList.toggle("is-active", target === "global");
  el.adminNotificationPersonalPanel?.classList.toggle("is-active", target === "personal");
}

function populateAdminNotificationUsers() {
  if (!el.adminNotificationUserSelect) return;
  const previous = el.adminNotificationUserSelect.value;
  setHtml(el.adminNotificationUserSelect, "");
  const blank = document.createElement("option");
  blank.value = "";
  blank.textContent = "Выберите пользователя";
  el.adminNotificationUserSelect.appendChild(blank);

  const users = (state.admin.users || []).filter((user) => Boolean(user.is_active));
  users.forEach((user) => {
    const option = document.createElement("option");
    option.value = String(user.id || "");
    option.textContent = `${user.username || `#${user.id}`} (#${user.id})`;
    el.adminNotificationUserSelect.appendChild(option);
  });
  if (users.some((user) => String(user.id || "") === previous)) {
    el.adminNotificationUserSelect.value = previous;
  }
}

function notificationScopeLabel(scope) {
  return String(scope || "global") === "personal" ? "Личное" : "Общее";
}

function renderAdminNotificationsHistory() {
  if (!el.adminNotificationsHistory) return;
  const items = Array.isArray(state.admin.notifications) ? state.admin.notifications : [];
  if (!items.length) {
    el.adminNotificationsHistory.classList.add("muted");
    el.adminNotificationsHistory.textContent = "Оповещений пока нет.";
    return;
  }

  el.adminNotificationsHistory.classList.remove("muted");
  setHtml(el.adminNotificationsHistory, "");
  items.forEach((item) => {
    const row = document.createElement("article");
    row.className = "ticket-item admin-notification-history-item";
    const scope = String(item.scope || "global");
    const target = scope === "personal"
      ? `${item.target_username || "пользователь"} (#${item.target_user_id || "—"})`
      : `всем активным пользователям: ${Number(item.recipients_count || 0)}`;
    setHtml(row, `
      <div>
        <div class="ticket-item__meta">#${escapeHtml(String(item.id || ""))} • ${escapeHtml(notificationScopeLabel(scope))} • ${escapeHtml(formatMskDateTimeLabel(item.created_at, ""))}</div>
        <div class="ticket-item__status">${escapeHtml(item.title || "Без заголовка")}</div>
        <div class="ticket-item__question">${escapeHtml(target)} • прочитано: ${escapeHtml(String(item.read_count || 0))}</div>
      </div>
      <button type="button" class="danger mini" data-delete-notification="${escapeHtml(String(item.id || ""))}">Удалить</button>
      <div class="ticket-item__answer">${escapeHtml(item.message || "")}</div>
    `);
    el.adminNotificationsHistory.appendChild(row);
  });

  el.adminNotificationsHistory.querySelectorAll("[data-delete-notification]").forEach((button) => {
    button.addEventListener("click", async () => {
      const id = Number(button.dataset.deleteNotification || 0);
      if (id <= 0) return;
      if (!window.confirm(`Удалить оповещение #${id}? У пользователей оно тоже пропадёт.`)) return;
      setButtonBusy(button, true, "Удаляем...");
      try {
        await api(`/api/admin/notifications/${encodeURIComponent(String(id))}`, {
          method: "DELETE",
          body: { user_id: state.userId },
        });
        await loadAdminNotifications();
        await loadNotifications({ silent: true });
        setResult(el.adminNotificationsResult, `Оповещение #${id} удалено.`);
        showToast("Оповещение удалено.");
      } catch (error) {
        setResult(el.adminNotificationsResult, errorText(error, "Не удалось удалить оповещение."), true);
      } finally {
        setButtonBusy(button, false);
      }
    });
  });
}

async function loadAdminNotifications() {
  if (!el.adminNotificationsHistory) return;
  setHtml(el.adminNotificationsHistory, "<div class='muted'>Загрузка оповещений...</div>");
  const data = await api("/api/admin/notifications?limit=200");
  state.admin.notifications = Array.isArray(data.items) ? data.items : [];
  renderAdminNotificationsHistory();
}

async function sendAdminNotification(scope) {
  const isPersonal = scope === "personal";
  const titleInput = isPersonal ? el.adminNotificationPersonalTitle : el.adminNotificationGlobalTitle;
  const messageInput = isPersonal ? el.adminNotificationPersonalMessage : el.adminNotificationGlobalMessage;
  const button = isPersonal ? el.adminNotificationPersonalSendBtn : el.adminNotificationGlobalSendBtn;
  const title = String(titleInput?.value || "").trim();
  const message = String(messageInput?.value || "").trim();
  const targetUserId = isPersonal ? Number(el.adminNotificationUserSelect?.value || 0) : 0;

  if (!title) {
    setResult(el.adminNotificationsResult, "Введите заголовок оповещения.", true);
    return;
  }
  if (!message) {
    setResult(el.adminNotificationsResult, "Введите текст оповещения.", true);
    return;
  }
  if (isPersonal && targetUserId <= 0) {
    setResult(el.adminNotificationsResult, "Выберите пользователя.", true);
    return;
  }

  setButtonBusy(button, true, "Отправляем...");
  try {
    await api("/api/admin/notifications", {
      method: "POST",
      body: {
        user_id: state.userId,
        scope: isPersonal ? "personal" : "global",
        target_user_id: targetUserId,
        title,
        message,
      },
    });
    if (titleInput) titleInput.value = "";
    if (messageInput) messageInput.value = "";
    await loadAdminNotifications();
    await loadNotifications({ silent: true });
    setResult(el.adminNotificationsResult, isPersonal ? "Личное оповещение отправлено." : "Общее оповещение отправлено.");
    showToast("Оповещение отправлено.");
  } catch (error) {
    setResult(el.adminNotificationsResult, errorText(error, "Не удалось отправить оповещение."), true);
  } finally {
    setButtonBusy(button, false);
  }
}

function populateAdminCategorySelects() {
  const categories = state.admin.categories || [];
  const previousByTarget = new Map([
    [el.adminCategorySelect, el.adminCategorySelect?.value || ""],
    [el.adminSausageCategorySelect, el.adminSausageCategorySelect?.value || ""],
    [el.adminRecipeCategorySelect, el.adminRecipeCategorySelect?.value || ""],
  ]);
  const targets = [
    el.adminCategorySelect,
    el.adminSausageCategorySelect,
    el.adminRecipeCategorySelect,
  ];

  targets.forEach((target) => {
    setHtml(target, "");
    categories.forEach((cat) => {
      const opt = document.createElement("option");
      opt.value = cat.slug;
      opt.textContent = cat.title;
      target.appendChild(opt);
    });
  });

  if (categories.length > 0) {
    targets.forEach((target) => {
      const previous = String(previousByTarget.get(target) || "").trim();
      const nextValue = categories.some((cat) => cat.slug === previous)
        ? previous
        : categories[0].slug;
      target.value = nextValue;
    });
  }

  syncAdminCategoryFields();
  syncAdminSausageSelect();
  syncAdminRecipeSausageSelect();
}

function syncAdminCategoryFields() {
  const slug = el.adminCategorySelect.value;
  const category = (state.admin.categories || []).find((c) => c.slug === slug);
  el.adminCategoryTitle.value = category?.title || "";
}

function syncAdminSausageSelect() {
  const categorySlug = el.adminSausageCategorySelect.value;
  const category = (state.admin.categories || []).find((c) => c.slug === categorySlug);
  const list = category?.sausages || [];

  setHtml(el.adminSausageSelect, "");
  appendAdminCreateOption(el.adminSausageSelect, "Новый сорт");
  list.forEach((s) => {
    const opt = document.createElement("option");
    opt.value = s.slug;
    opt.textContent = s.display_name;
    el.adminSausageSelect.appendChild(opt);
  });

  el.adminSausageSelect.value = "";
  el.adminSausageName.value = "";
  refreshAdminDeleteButtons();
}

function populateAdminHandbookSectionSelect() {
  const sections = state.admin.handbookSections || [];
  setHtml(el.adminHandbookSectionSelect, "");
  sections.forEach((section) => {
    const opt = document.createElement("option");
    opt.value = section.key;
    opt.textContent = section.title || section.key;
    el.adminHandbookSectionSelect.appendChild(opt);
  });
  if (sections.length > 0) {
    el.adminHandbookSectionSelect.value = sections[0].key;
    syncAdminHandbookArticles();
    return;
  }
  setHtml(el.adminHandbookArticleSelect, "");
  el.adminHandbookArticleKey.value = "";
  el.adminHandbookArticleTitle.value = "";
  el.adminHandbookArticleText.value = "";
  el.adminHandbookArticleLink.value = "";
}

function syncAdminHandbookArticles() {
  const sectionKey = el.adminHandbookSectionSelect.value;
  const section = (state.admin.handbookSections || []).find((item) => item.key === sectionKey);
  const articles = section?.articles || [];
  setHtml(el.adminHandbookArticleSelect, "");
  appendAdminCreateOption(el.adminHandbookArticleSelect, "Новая статья");
  articles.forEach((article) => {
    const opt = document.createElement("option");
    opt.value = article.key;
    opt.textContent = article.title || article.key;
    el.adminHandbookArticleSelect.appendChild(opt);
  });
  el.adminHandbookArticleSelect.value = "";
  el.adminHandbookArticleKey.value = "";
  el.adminHandbookArticleTitle.value = "";
  el.adminHandbookArticleText.value = "";
  el.adminHandbookArticleLink.value = "";
  refreshAdminDeleteButtons();
}

function syncAdminHandbookArticleFields() {
  const sectionKey = el.adminHandbookSectionSelect.value;
  const articleKey = el.adminHandbookArticleSelect.value;
  const section = (state.admin.handbookSections || []).find((item) => item.key === sectionKey);
  const article = (section?.articles || []).find((item) => item.key === articleKey);
  if (!article) {
    el.adminHandbookArticleKey.value = "";
    el.adminHandbookArticleTitle.value = "";
    el.adminHandbookArticleText.value = "";
    el.adminHandbookArticleLink.value = "";
    return;
  }
  el.adminHandbookArticleKey.value = article.key || "";
  el.adminHandbookArticleTitle.value = article.title || "";
  el.adminHandbookArticleText.value = article.text || "";
  el.adminHandbookArticleLink.value = article.link || "";
}

function syncAdminRecipeSausageSelect() {
  const categorySlug = el.adminRecipeCategorySelect.value;
  const category = (state.admin.categories || []).find((c) => c.slug === categorySlug);
  const list = category?.sausages || [];
  const previousSlug = String(el.adminRecipeSausageSelect?.value || "").trim();

  setHtml(el.adminRecipeSausageSelect, "");
  appendAdminCreateOption(el.adminRecipeSausageSelect, "Новый рецепт");
  list.forEach((s) => {
    const opt = document.createElement("option");
    opt.value = s.slug;
    opt.textContent = s.display_name;
    el.adminRecipeSausageSelect.appendChild(opt);
  });

  const keepSlug = previousSlug && list.some((item) => item.slug === previousSlug);
  if (keepSlug) {
    el.adminRecipeSausageSelect.value = previousSlug;
    void loadAdminRecipe().catch((error) => {
      setResult(el.adminRecipeResult, error.message, true);
    });
  } else {
    el.adminRecipeSausageSelect.value = "";
    clearAdminRecipeEditor();
  }
  refreshAdminDeleteButtons();
}

function clearAdminRecipeEditor() {
  state.admin.recipeLoadRequestId = (state.admin.recipeLoadRequestId || 0) + 1;
  state.admin.recipeLoadedSlug = "";
  if (el.adminRecipeName) {
    el.adminRecipeName.value = "";
  }
  if (el.adminRecipeSlug) {
    el.adminRecipeSlug.value = "";
    el.adminRecipeSlug.readOnly = false;
  }
  el.adminRecipeText.value = "";
  if (el.adminRecipePhotoPath) {
    el.adminRecipePhotoPath.value = "";
  }
  el.adminRecipePhotoId.value = "";
  setHtml(el.adminIngredientsList, "");
  state.admin.recipeOriginalIngredientNames = new Set();
  if (el.adminRecipePhotoPreview) {
    el.adminRecipePhotoPreview.classList.add("hidden");
    el.adminRecipePhotoPreview.removeAttribute("src");
  }
}

function getRecipeBindableStoreProducts() {
  const byId = new Map();
  [...(state.admin.storeProducts || []), ...(state.store.products || [])].forEach((p) => {
    const id = Number(p?.id || 0);
    if (id > 0 && !byId.has(id)) byId.set(id, p);
  });
  return Array.from(byId.values()).sort((a, b) => String(a.title || "").localeCompare(String(b.title || ""), "ru"));
}

function fillRecipeProductSelect(select, selectedProductId = 0) {
  if (!select) return;
  const selected = String(selectedProductId || select.value || 0);
  const products = getRecipeBindableStoreProducts();
  setHtml(select, "");
  const emptyProduct = document.createElement("option");
  emptyProduct.value = "0";
  emptyProduct.textContent = products.length
    ? "Товар для корзины не выбран"
    : "Нет товаров магазина — откройте/обновите вкладку «Магазин»";
  select.appendChild(emptyProduct);
  products.forEach((p) => {
    const opt = document.createElement("option");
    opt.value = String(p.id);
    opt.textContent = `${p.title || "Товар"}${p.weight_grams ? ` • ${p.weight_grams} г` : ""}`;
    select.appendChild(opt);
  });
  select.value = products.some((p) => String(p.id) === selected) ? selected : "0";
}

function refreshRecipeProductSelectOptions() {
  Array.from(el.adminIngredientsList?.querySelectorAll('select[data-field="product_id"], select[data-option-field="product_id"]') || []).forEach((select) => {
    fillRecipeProductSelect(select, select.value || 0);
  });
}

async function ensureAdminStoreProductsLoaded() {
  if ((state.admin.storeProducts || []).length) return;
  await refreshAdminStore();
}

function makeIngredientOptionRow(item = {}) {
  const row = document.createElement("div");
  row.className = "ingredient-option-row";
  row.dataset.optionKey = String(item.option_key || "");

  const branch = document.createElement("div");
  branch.className = "ingredient-option-branch";
  branch.textContent = "↳ Вариант";

  const name = document.createElement("input");
  name.type = "text";
  name.placeholder = "Название варианта";
  name.value = item.full_name || "";
  name.dataset.optionField = "full_name";

  const qty = document.createElement("input");
  qty.type = "text";
  qty.inputMode = "decimal";
  qty.placeholder = "Количество на 1 кг";
  qty.value = item.value ?? item.quantity ?? "";
  qty.dataset.optionField = "quantity";

  const key = document.createElement("input");
  key.type = "text";
  key.placeholder = "Ключ цены варианта";
  key.value = item.short_key || item.short_label || item.full_name || "";
  key.dataset.optionField = "short_label";

  const link = document.createElement("input");
  link.type = "text";
  link.placeholder = "Ссылка (необязательно)";
  link.value = item.link || "";
  link.dataset.optionField = "link";

  const product = document.createElement("select");
  product.dataset.optionField = "product_id";
  fillRecipeProductSelect(product, item.product_id || 0);

  const remove = document.createElement("button");
  remove.type = "button";
  remove.textContent = "Удалить";
  remove.className = "danger mini";
  remove.addEventListener("click", () => row.remove());

  row.append(branch, name, qty, key, link, product, remove);
  return row;
}

function seedIngredientOptions(ingredientRow) {
  const optionsList = ingredientRow.querySelector(".ingredient-options-list");
  if (!optionsList) return;
  if (optionsList.children.length > 0) {
    optionsList.appendChild(makeIngredientOptionRow());
    return;
  }

  const name = ingredientRow.querySelector('[data-field="full_name"]')?.value?.trim() || "";
  const quantity = ingredientRow.querySelector('[data-field="quantity"]')?.value?.trim() || "";
  const shortLabel = ingredientRow.querySelector('[data-field="short_label"]')?.value?.trim() || "";
  const link = ingredientRow.querySelector('[data-field="link"]')?.value?.trim() || "";
  const productId = Number(ingredientRow.querySelector('[data-field="product_id"]')?.value || 0) || 0;
  const splitNames = name.split(/\s+или\s+/i).map((value) => value.trim()).filter(Boolean);
  const optionNames = splitNames.length >= 2 ? splitNames : [name, ""];

  optionNames.forEach((optionName, index) => {
    optionsList.appendChild(makeIngredientOptionRow({
      full_name: optionName,
      quantity,
      short_label: splitNames.length >= 2 ? optionName : (index === 0 ? shortLabel : optionName),
      link: index === 0 ? link : "",
      product_id: index === 0 ? productId : 0,
    }));
  });
}

function makeIngredientRow(item = {}) {
  const row = document.createElement("div");
  row.className = "ingredient-row";

  const main = document.createElement("div");
  main.className = "ingredient-row-main";

  const name = document.createElement("input");
  name.type = "text";
  name.placeholder = "Название ингредиента или группы выбора";
  name.value = item.full_name || "";
  name.dataset.field = "full_name";

  const qty = document.createElement("input");
  qty.type = "text";
  qty.inputMode = "decimal";
  qty.placeholder = "Количество на 1 кг";
  qty.value = item.value ?? item.quantity ?? "";
  qty.dataset.field = "quantity";

  const key = document.createElement("input");
  key.type = "text";
  key.placeholder = "Ключ цены";
  key.value = item.short_key || item.short_label || item.full_name || "";
  key.dataset.field = "short_label";

  const link = document.createElement("input");
  link.type = "text";
  link.placeholder = "Ссылка (необязательно)";
  link.value = item.link || "";
  link.dataset.field = "link";

  const product = document.createElement("select");
  product.dataset.field = "product_id";
  fillRecipeProductSelect(product, item.product_id || 0);

  const addOption = document.createElement("button");
  addOption.type = "button";
  addOption.textContent = "+ Вариант";
  addOption.className = "chip mini";
  addOption.addEventListener("click", () => seedIngredientOptions(row));

  const remove = document.createElement("button");
  remove.type = "button";
  remove.textContent = "Удалить";
  remove.className = "danger mini";
  remove.addEventListener("click", () => row.remove());

  const actions = document.createElement("div");
  actions.className = "ingredient-row-actions";
  actions.append(addOption, remove);
  main.append(name, qty, key, link, product, actions);

  const optionsList = document.createElement("div");
  optionsList.className = "ingredient-options-list";
  (item.options || []).forEach((option) => {
    optionsList.appendChild(makeIngredientOptionRow(option));
  });

  row.append(main, optionsList);
  return row;
}

function collectRecipeIngredients() {
  const rows = Array.from(el.adminIngredientsList.querySelectorAll(":scope > .ingredient-row"));
  const payload = [];
  const names = new Set();

  rows.forEach((row, rowIndex) => {
    const fullName = row.querySelector(':scope > .ingredient-row-main [data-field="full_name"]')?.value?.trim() || "";
    const quantity = row.querySelector(':scope > .ingredient-row-main [data-field="quantity"]')?.value?.trim() || "";
    const shortLabel = row.querySelector(':scope > .ingredient-row-main [data-field="short_label"]')?.value?.trim() || fullName;
    const link = row.querySelector(':scope > .ingredient-row-main [data-field="link"]')?.value?.trim() || "";
    const productId = Number(row.querySelector(':scope > .ingredient-row-main [data-field="product_id"]')?.value || 0) || 0;
    const optionRows = Array.from(row.querySelectorAll(":scope > .ingredient-options-list > .ingredient-option-row"));
    const options = optionRows.map((optionRow, optionIndex) => {
      const optionName = optionRow.querySelector('[data-option-field="full_name"]')?.value?.trim() || "";
      const optionQuantity = optionRow.querySelector('[data-option-field="quantity"]')?.value?.trim() || "";
      if (!optionName || !optionQuantity) {
        throw new Error(`Ингредиент ${rowIndex + 1}, вариант ${optionIndex + 1}: заполните название и количество.`);
      }
      return {
        option_key: String(optionRow.dataset.optionKey || ""),
        full_name: optionName,
        quantity: optionQuantity,
        short_label: optionRow.querySelector('[data-option-field="short_label"]')?.value?.trim() || optionName,
        link: optionRow.querySelector('[data-option-field="link"]')?.value?.trim() || "",
        product_id: Number(optionRow.querySelector('[data-option-field="product_id"]')?.value || 0) || 0,
      };
    });

    if (!fullName) {
      throw new Error(`Ингредиент ${rowIndex + 1}: укажите название.`);
    }
    if (names.has(fullName)) {
      throw new Error(`Ингредиент ${rowIndex + 1}: название «${fullName}» повторяется.`);
    }
    names.add(fullName);
    if (options.length === 1) {
      throw new Error(`Ингредиент ${rowIndex + 1}: добавьте второй вариант или удалите единственный.`);
    }
    if (!quantity && options.length === 0) {
      throw new Error(`Ингредиент ${rowIndex + 1}: укажите количество.`);
    }

    payload.push({
      full_name: fullName,
      quantity,
      short_label: shortLabel || fullName,
      link,
      product_id: productId,
      options,
    });
  });

  return payload;
}

function assertAdminRecipeLoaded(slug) {
  if (slug && state.admin.recipeLoadedSlug !== slug) {
    throw new Error("Дождитесь загрузки выбранного рецепта перед сохранением.");
  }
}

async function loadAdminRecipe() {
  const slug = el.adminRecipeSausageSelect.value;
  if (!slug) return;
  const categorySlug = el.adminRecipeCategorySelect.value;
  const requestId = (state.admin.recipeLoadRequestId || 0) + 1;
  state.admin.recipeLoadRequestId = requestId;
  state.admin.recipeLoadedSlug = "";
  const isCurrent = () => state.admin.recipeLoadRequestId === requestId
    && el.adminRecipeSausageSelect.value === slug
    && el.adminRecipeCategorySelect.value === categorySlug;

  await ensureAdminStoreProductsLoaded();
  if (!isCurrent()) return;
  const recipe = await api(`/api/recipes/${encodeURIComponent(slug)}`);
  if (!isCurrent()) return;
  if (el.adminRecipeName) {
    el.adminRecipeName.value = recipe.display_name || "";
  }
  if (el.adminRecipeSlug) {
    el.adminRecipeSlug.value = recipe.slug || slug;
    el.adminRecipeSlug.readOnly = true;
  }
  el.adminRecipeText.value = recipe.recipe_text || "";
  if (el.adminRecipePhotoPath) {
    el.adminRecipePhotoPath.value = recipe.recipe_photo_path || "";
  }
  el.adminRecipePhotoId.value = recipe.recipe_photo_id || "";
  if (el.adminRecipePhotoFile) {
    el.adminRecipePhotoFile.value = "";
  }
  if (el.adminRecipePhotoUploadHint) {
    const photoPath = recipe.recipe_photo_path || "";
    const photoUrl = recipe.recipe_photo_url || "";
    if (photoPath) {
      const suffix = photoUrl ? ` (${photoUrl})` : "";
      el.adminRecipePhotoUploadHint.textContent = `Текущее фото: ${photoPath}${suffix}`;
      el.adminRecipePhotoUploadHint.classList.remove("error-text");
    } else {
      el.adminRecipePhotoUploadHint.textContent = "Фото не задано.";
      el.adminRecipePhotoUploadHint.classList.remove("error-text");
    }
  }
  if (el.adminRecipePhotoPreview) {
    const photoUrl = String(recipe.recipe_photo_url || "").trim();
    if (photoUrl) {
      el.adminRecipePhotoPreview.src = photoUrl;
      el.adminRecipePhotoPreview.classList.remove("hidden");
    } else {
      el.adminRecipePhotoPreview.classList.add("hidden");
      el.adminRecipePhotoPreview.removeAttribute("src");
    }
  }

  setHtml(el.adminIngredientsList, "");
  state.admin.recipeOriginalIngredientNames = new Set();
  (recipe.ingredients || []).forEach((item) => {
    el.adminIngredientsList.appendChild(makeIngredientRow(item));
    if (item.full_name) {
      state.admin.recipeOriginalIngredientNames.add(String(item.full_name));
    }
  });
  state.admin.recipeLoadedSlug = slug;
}

function populateAdminFaqSelect() {
  const list = state.admin.faq || [];
  setHtml(el.adminFaqSelect, "");

  appendAdminCreateOption(el.adminFaqSelect, "Новая запись");

  list.forEach((row) => {
    const opt = document.createElement("option");
    opt.value = String(row.id);
    opt.textContent = `#${row.id} ${row.question}`;
    el.adminFaqSelect.appendChild(opt);
  });

  el.adminFaqSelect.value = "";
  el.adminFaqQuestion.value = "";
  el.adminFaqAnswer.value = "";
  refreshAdminDeleteButtons();
}

function syncAdminFaqFields() {
  const id = Number(el.adminFaqSelect.value || 0);
  if (id <= 0) {
    el.adminFaqQuestion.value = "";
    el.adminFaqAnswer.value = "";
    return;
  }

  const row = (state.admin.faq || []).find((x) => Number(x.id) === id);
  el.adminFaqQuestion.value = row?.question || "";
  el.adminFaqAnswer.value = row?.answer || "";
}

function populateAdminEquipmentSelect() {
  const list = state.admin.equipment || [];
  setHtml(el.adminEquipmentSelect, "");

  appendAdminCreateOption(el.adminEquipmentSelect, "Новая позиция");

  list.forEach((item) => {
    const opt = document.createElement("option");
    opt.value = String(item.id);
    opt.textContent = `#${item.id} ${item.title}`;
    el.adminEquipmentSelect.appendChild(opt);
  });

  el.adminEquipmentSelect.value = "";
  clearEquipmentFields();
  refreshAdminDeleteButtons();
}

function clearEquipmentFields() {
  el.adminEquipmentCategoryName.value = "";
  el.adminEquipmentTitle.value = "";
  el.adminEquipmentDescription.value = "";
  el.adminEquipmentBuyLink.value = "";
  el.adminEquipmentBuyLabel.value = "Купить";
  el.adminEquipmentPhotoPath.value = "";
  el.adminEquipmentPhotoId.value = "";
  if (el.adminEquipmentPhotoFile) {
    el.adminEquipmentPhotoFile.value = "";
  }
  if (el.adminEquipmentPhotoUploadHint) {
    el.adminEquipmentPhotoUploadHint.textContent = "После загрузки файл попадет в /data, старое фото удаляется автоматически.";
    el.adminEquipmentPhotoUploadHint.classList.remove("error-text");
  }
  el.adminEquipmentSortOrder.value = "100";
  el.adminEquipmentActive.checked = true;
}

function syncAdminEquipmentFields() {
  const id = Number(el.adminEquipmentSelect.value || 0);
  if (id <= 0) {
    clearEquipmentFields();
    return;
  }

  const row = (state.admin.equipment || []).find((item) => Number(item.id) === id);
  if (!row) {
    clearEquipmentFields();
    return;
  }

  el.adminEquipmentCategoryName.value = row.category_name || "";
  el.adminEquipmentTitle.value = row.title || "";
  el.adminEquipmentDescription.value = row.description || "";
  el.adminEquipmentBuyLink.value = row.buy_link || "";
  el.adminEquipmentBuyLabel.value = row.buy_label || "Купить";
  el.adminEquipmentPhotoPath.value = row.photo_path || "";
  el.adminEquipmentPhotoId.value = row.photo_id || "";
  if (el.adminEquipmentPhotoFile) {
    el.adminEquipmentPhotoFile.value = "";
  }
  if (el.adminEquipmentPhotoUploadHint) {
    const photoUrl = row.photo_url ? ` (${row.photo_url})` : "";
    el.adminEquipmentPhotoUploadHint.textContent = `Текущее фото: ${row.photo_path || "не задано"}${photoUrl}`;
    el.adminEquipmentPhotoUploadHint.classList.remove("error-text");
  }
  el.adminEquipmentSortOrder.value = String(row.sort_order ?? 100);
  el.adminEquipmentActive.checked = Number(row.is_active || 0) === 1;
}

async function uploadAdminEquipmentPhoto() {
  const file = el.adminEquipmentPhotoFile?.files?.[0];
  if (!file) {
    throw new Error("Выберите файл изображения.");
  }
  const fd = new FormData();
  fd.append("file", file);
  fd.append("user_id", String(state.userId || 0));
  if (el.adminEquipmentPhotoPath?.value?.trim()) {
    fd.append("replace_path", el.adminEquipmentPhotoPath.value.trim());
  }
  const payload = await api("/api/admin/equipment/photo", {
    method: "POST",
    body: fd,
    isFormData: true,
  });
  const photoPath = String(payload?.photo_path || "").trim();
  const photoUrl = String(payload?.photo_url || "").trim();
  if (photoPath) {
    el.adminEquipmentPhotoPath.value = photoPath;
  }
  if (el.adminEquipmentPhotoUploadHint) {
    el.adminEquipmentPhotoUploadHint.textContent = photoUrl
      ? `Файл загружен: ${photoPath} (${photoUrl})`
      : `Файл загружен: ${photoPath}`;
    el.adminEquipmentPhotoUploadHint.classList.remove("error-text");
  }
  if (el.adminEquipmentPhotoFile) {
    el.adminEquipmentPhotoFile.value = "";
  }
  return payload;
}

async function uploadAdminRecipePhoto() {
  const file = el.adminRecipePhotoFile?.files?.[0];
  if (!file) {
    throw new Error("Выберите файл изображения.");
  }
  const fd = new FormData();
  fd.append("file", file);
  fd.append("user_id", String(state.userId || 0));
  if (el.adminRecipePhotoPath?.value?.trim()) {
    fd.append("replace_path", el.adminRecipePhotoPath.value.trim());
  }
  const payload = await api("/api/admin/recipes/photo", {
    method: "POST",
    body: fd,
    isFormData: true,
  });
  const photoPath = String(payload?.photo_path || "").trim();
  const photoUrl = String(payload?.photo_url || "").trim();
  if (el.adminRecipePhotoPath && photoPath) {
    el.adminRecipePhotoPath.value = photoPath;
  }
  if (el.adminRecipePhotoUploadHint) {
    el.adminRecipePhotoUploadHint.textContent = photoUrl
      ? `Файл загружен: ${photoPath} (${photoUrl})`
      : `Файл загружен: ${photoPath}`;
    el.adminRecipePhotoUploadHint.classList.remove("error-text");
  }
  if (el.adminRecipePhotoPreview) {
    if (photoUrl) {
      el.adminRecipePhotoPreview.src = photoUrl;
      el.adminRecipePhotoPreview.classList.remove("hidden");
    } else {
      el.adminRecipePhotoPreview.classList.add("hidden");
      el.adminRecipePhotoPreview.removeAttribute("src");
    }
  }
  if (el.adminRecipePhotoFile) {
    el.adminRecipePhotoFile.value = "";
  }
  return payload;
}

function populateAdminUsersSelect() {
  if (!el.adminUsersSelect) return;
  const users = state.admin.users || [];
  setHtml(el.adminUsersSelect, "");

  const blank = document.createElement("option");
  blank.value = "";
  blank.textContent = "Выберите пользователя";
  el.adminUsersSelect.appendChild(blank);

  users.forEach((user) => {
    const opt = document.createElement("option");
    opt.value = String(user.id || 0);
    const role = user.is_admin ? "admin" : "user";
    const status = user.is_active ? "active" : "disabled";
    const email = String(user.email || "").trim();
    const oauthProvider = String(user.oauth_provider_label || user.oauth_provider || "").trim();
    const tier = humanTierTitle(user.subscription_tier || "none");
    const subStatus = String(user.subscription_status || "inactive").trim().toLowerCase() || "inactive";
    opt.textContent = `#${user.id} ${user.username}${email ? ` • ${email}` : ""}${oauthProvider ? ` • ${oauthProvider}` : ""} (${role}, ${status}, ${tier}/${subStatus})`;
    el.adminUsersSelect.appendChild(opt);
  });

  el.adminUsersSelect.value = "";
  if (el.adminUsersInfo) {
    el.adminUsersInfo.textContent = users.length
      ? `Всего пользователей: ${users.length}.`
      : "Пользователей пока нет.";
  }
  populateAdminNotificationUsers();
}

function getAdminBanBlockReason(user) {
  if (!user || typeof user !== "object") return "Сначала выберите пользователя.";
  if (Number(user.id || 0) === Number(state.userId || 0)) return "Нельзя забанить самого себя.";
  if (Boolean(user.is_admin)) return "Администраторов нельзя банить.";
  return "";
}

function syncAdminBanProtectionState(user) {
  const reason = getAdminBanBlockReason(user);
  [el.adminBanUserBtn, el.adminUserIsActive].forEach((control) => {
    if (!control) return;
    control.disabled = Boolean(reason);
    if (reason) {
      control.title = reason;
    } else {
      control.removeAttribute("title");
    }
  });
}

function syncAdminUserFields() {
  if (!el.adminUsersSelect) return;
  const userId = Number(el.adminUsersSelect.value || 0);
  const user = (state.admin.users || []).find((item) => Number(item.id || 0) === userId);
  if (el.adminUserUsername) {
    el.adminUserUsername.value = user?.username || "";
    el.adminUserUsername.disabled = !user;
  }
  if (el.adminUserPassword) {
    el.adminUserPassword.value = "";
    el.adminUserPassword.disabled = !user;
  }
  if (!user) {
    if (el.adminUsersInfo) {
      el.adminUsersInfo.textContent = state.admin.users?.length
        ? `Всего пользователей: ${state.admin.users.length}.`
        : "Пользователей пока нет.";
    }
    if (el.adminUserIsAdmin) {
      el.adminUserIsAdmin.checked = false;
    }
    if (el.adminUserIsActive) {
      el.adminUserIsActive.checked = false;
    }
    if (el.adminUserDossier) {
      el.adminUserDossier.textContent = "Выберите пользователя в списке.";
    }
    if (el.adminUserStoreSausages) {
      el.adminUserStoreSausages.value = "0";
    }
    if (el.adminUserProfile) {
      el.adminUserProfile.textContent = "Выберите пользователя в списке.";
    }
    if (el.adminUserProfileDossier) {
      el.adminUserProfileDossier.textContent = "Выберите пользователя в списке.";
    }
    if (el.adminUserAccessTier) {
      el.adminUserAccessTier.value = "premium";
    }
    if (el.adminUserAccessHours) {
      el.adminUserAccessHours.value = "24";
    }
    if (el.adminUserAccessMode) {
      el.adminUserAccessMode.value = "replace";
    }
    syncAdminBanProtectionState(null);
    return;
  }

  if (el.adminUserIsAdmin) {
    el.adminUserIsAdmin.checked = Boolean(user.is_admin);
  }
  if (el.adminUserIsActive) {
    el.adminUserIsActive.checked = Boolean(user.is_active);
  }

  if (el.adminUserStoreSausages) {
    el.adminUserStoreSausages.value = String(Math.max(0, Number(user.store_sausages || 0)));
  }
  if (el.adminUserAccessTier) {
    el.adminUserAccessTier.value = "premium";
  }
  if (el.adminUserAccessHours && !String(el.adminUserAccessHours.value || "").trim()) {
    el.adminUserAccessHours.value = "24";
  }
  if (el.adminUsersInfo) {
    const role = user.is_admin ? "Админ" : "Обычный пользователь";
    const status = user.is_active ? "Активен" : "Отключен";
    const twofa = user.totp_enabled ? "2FA: включен" : (user.twofa_enrolled ? "2FA: отвязан" : "2FA: не подключен");
    const tier = humanTierTitle(user.subscription_tier || "none");
    const subscriptionStatus = String(user.subscription_status || "").trim() || "inactive";
    const until = user.subscription_active_until ? `, до: ${formatMskDateTimeLabel(user.subscription_active_until, user.subscription_active_until)}` : "";
    const lastLogin = user.last_login_at ? `, вход: ${formatMskDateTimeLabel(user.last_login_at, user.last_login_at)}` : "";
    el.adminUsersInfo.textContent = `${role}, ${status}, ${twofa}, подписка: ${tier} (${subscriptionStatus}${until})${lastLogin}`;
  }
  renderAdminUserProfile(user);
  if (el.adminUserProfileDossier) {
    void loadAdminUserDossier(userId, el.adminUserProfileDossier);
  }
  if (el.adminDbUserIdFilter && !String(el.adminDbUserIdFilter.value || "").trim()) {
    el.adminDbUserIdFilter.value = String(userId);
  }
  syncAdminBanProtectionState(user);
}

function getSelectedAdminUser() {
  const userId = Number(el.adminUsersSelect?.value || 0);
  if (userId <= 0) {
    throw new Error("Выберите пользователя в списке.");
  }
  const user = (state.admin.users || []).find((item) => Number(item.id || 0) === userId);
  if (!user) {
    throw new Error("Пользователь не найден.");
  }
  return { userId, user };
}

function formatDossierValue(value) {
  if (value === null || value === undefined) return "—";
  if (typeof value === "boolean") return value ? "true" : "false";
  if (typeof value === "number") return String(value);
  if (typeof value === "string") {
    if (looksLikeDateTime(value)) {
      return formatMskDateTimeLabel(value, value);
    }
    return value || "—";
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch (_e) {
    return String(value);
  }
}

function renderAdminUserProfile(user) {
  if (!el.adminUserProfile) return;
  if (!user || typeof user !== "object") {
    el.adminUserProfile.textContent = "Выберите пользователя в списке.";
    return;
  }

  const keyLabels = {
    id: "ID",
    username: "Логин",
    email: "Email",
    email_verified: "Email подтвержден",
    first_name: "Имя",
    last_name: "Фамилия",
    phone: "Телефон из аккаунта",
    oauth_provider: "Провайдер входа",
    oauth_provider_label: "Источник входа",
    oauth_profile_consent_at: "Согласие на данные OAuth",
    is_admin: "Админ",
    is_active: "Активен",
    totp_enabled: "2FA включен",
    twofa_enrolled: "2FA подключен",
    created_at: "Создан",
    updated_at: "Обновлен",
    last_login_at: "Последний вход",
    pd_consent_at: "Согласие ПД",
    cookie_notice_ack_at: "Согласие cookie",
    subscription_tier: "Уровень подписки",
    subscription_status: "Статус подписки",
    subscription_active_until: "Оплачен до",
    subscription_autopay_enabled: "Автоплатеж",
    subscription_autopay_method: "Метод автоплатежа",
    subscription_has_rebill: "Есть RebillId",
    subscription_has_sbp: "Есть SBP токен",
    subscription_source: "Источник доступа",
    subscription_last_order_id: "Последний order_id",
    subscription_last_payment_id: "Последний payment_id",
    subscription_updated_at: "Подписка обновлена",
    store_sausages: "Колбаски",
  };
  const primaryKeys = Object.keys(keyLabels);
  const known = new Set(primaryKeys);
  const extraKeys = Object.keys(user).filter((key) => !known.has(key)).sort();

  const formatProfileValue = (key, value) => {
    if (key === "subscription_tier") {
      return humanTierTitle(String(value || "none").trim().toLowerCase());
    }
    if (value === null || value === undefined || value === "") return "—";
    if (typeof value === "boolean") return value ? "Да" : "Нет";
    if (typeof value === "number") return String(value);
    if (typeof value === "string" && (key.endsWith("_at") || key.endsWith("_until") || key.endsWith("_from"))) {
      return formatMskDateTimeLabel(value, value);
    }
    if (typeof value === "string") return value;
    try {
      return JSON.stringify(value, null, 2);
    } catch (_error) {
      return String(value);
    }
  };

  const renderLine = (key, label) => {
    const raw = formatProfileValue(key, user[key]);
    const isMultiline = String(raw).includes("\n");
    if (isMultiline) {
      return `<div><strong>${escapeHtml(label)}:</strong> <pre class="pre-wrap">${escapeHtml(String(raw))}</pre></div>`;
    }
    return `<div><strong>${escapeHtml(label)}:</strong> ${escapeHtml(String(raw))}</div>`;
  };

  const knownLines = primaryKeys.map((key) => renderLine(key, keyLabels[key]));
  const extraLines = extraKeys.map((key) => renderLine(key, key));
  const profileHtml = [...knownLines, ...extraLines].join("");
  setHtml(
    el.adminUserProfile,
    `
      <div class="ticket-item__meta"><strong>Профиль пользователя #${escapeHtml(String(user.id || ""))}</strong></div>
      ${profileHtml}
    `,
  );
}

function renderAdminUserDossier(data, target = el.adminUserDossier) {
  if (!target) return;
  const tables = Array.isArray(data?.tables) ? data.tables : [];
  const referralData = data?.referrals && typeof data.referrals === "object" ? data.referrals : {};
  const invited = Array.isArray(referralData.invited) ? referralData.invited : [];
  const referredBy = referralData.referred_by && typeof referralData.referred_by === "object"
    ? referralData.referred_by
    : null;
  if (!tables.length && !invited.length && !referredBy) {
    setHtml(target, "<div class='muted'>По этому пользователю данных в таблицах не найдено.</div>");
    return;
  }

  const invitedHtml = invited.length
    ? invited.map((item) => {
      const reward = Number(item?.reward_kopecks || 0) / 100;
      const rewardLabel = item?.reversed_at
        ? `${reward.toLocaleString("ru-RU")} колбасок · отменено возвратом`
        : item?.rewarded_at
          ? `${reward.toLocaleString("ru-RU")} колбасок начислено`
          : "Первая покупка ещё не оплачена или акция была выключена";
      return `
        <article class="ticket-item">
          <div class="ticket-item__meta">Пользователь #${escapeHtml(String(item?.referred_user_id || ""))} · ${escapeHtml(String(item?.registered_at || ""))}</div>
          <div class="ticket-item__question"><strong>${escapeHtml(String(item?.username || "Без имени"))}</strong>${item?.email ? ` · ${escapeHtml(String(item.email))}` : ""}</div>
          <div class="ticket-item__answer">Первая покупка: ${escapeHtml(String(item?.first_paid_order_id || "ещё не было"))} · ${escapeHtml(rewardLabel)}</div>
        </article>`;
    }).join("")
    : "<div class='muted'>Пользователь пока никого не пригласил.</div>";
  const referredByHtml = referredBy
    ? `<div class="ticket-item__answer">Сам зарегистрирован по приглашению пользователя #${escapeHtml(String(referredBy.referrer_user_id || ""))} · <strong>${escapeHtml(String(referredBy.username || "Без имени"))}</strong>.</div>`
    : "";
  const referralsBlock = `
    <section class="admin-referral-dossier">
      <div class="ticket-item__meta"><strong>Реферальная программа</strong> · приглашено: ${invited.length}</div>
      ${referredByHtml}
      ${invitedHtml}
    </section>`;

  const blocks = tables.map((table) => {
    const tableName = escapeHtml(String(table?.table || ""));
    const total = Number(table?.total || 0);
    const rows = Array.isArray(table?.rows) ? table.rows : [];
    const rowsHtml = rows.length
      ? rows.map((row, idx) => {
        const lines = Object.entries(row || {})
          .map(([key, val]) => `<div><strong>${escapeHtml(String(key))}:</strong> <pre class="pre-wrap">${escapeHtml(formatDossierValue(val))}</pre></div>`)
          .join("");
        return `<details><summary>Запись #${idx + 1}</summary>${lines}</details>`;
      }).join("")
      : "<div class='muted'>Записей нет.</div>";
    return `
      <article class="ticket-item">
        <div class="ticket-item__meta"><strong>${tableName}</strong> • всего: ${total}</div>
        ${rowsHtml}
      </article>
    `;
  }).join("");

  setHtml(
    target,
    `
      <div class="muted">Снимок на: ${escapeHtml(String(data?.generated_at || "—"))}</div>
      ${referralsBlock}
      ${blocks}
    `,
  );
}

async function loadAdminUserDossier(userId, target = el.adminUserDossier) {
  if (!target) return;
  if (!userId || userId <= 0) {
    target.textContent = "Выберите пользователя в списке.";
    return;
  }
  setHtml(target, "<div class='muted'>Загружаем личное дело пользователя...</div>");
  try {
    const data = await api(`/api/admin/users/${encodeURIComponent(String(userId))}/dossier`);
    renderAdminUserDossier(data, target);
  } catch (error) {
    setResult(target, error.message || "Не удалось загрузить личное дело пользователя.", true);
  }
}

function readAdminDbLogFilters() {
  return {
    source: String(el.adminDbSourceFilter?.value || "all").trim().toLowerCase() || "all",
    level: String(el.adminDbLevelFilter?.value || "all").trim().toLowerCase() || "all",
    user_id: Number(el.adminDbUserIdFilter?.value || 0) || 0,
    search: String(el.adminDbSearchFilter?.value || "").trim(),
    limit: Number(el.adminDbLimitFilter?.value || 100) || 100,
    offset: Math.max(0, Number(state.admin.dbLog.offset || 0)),
    auto_refresh: Boolean(el.adminDbAutoRefresh?.checked),
  };
}

function renderAdminDbLogList() {
  if (!el.adminDbLogList) return;
  const items = Array.isArray(state.admin.dbLog.items) ? state.admin.dbLog.items : [];
  if (!items.length) {
    setHtml(el.adminDbLogList, "<div class='muted'>События не найдены по текущим фильтрам.</div>");
  } else {
    const html = items.map((item, idx) => {
      const ts = escapeHtml(formatMskDateTimeLabel(item?.ts, String(item?.ts || "")));
      const source = escapeHtml(String(item?.source || ""));
      const level = escapeHtml(String(item?.level || ""));
      const event = escapeHtml(String(item?.event || ""));
      const userId = Number(item?.user_id || 0);
      const message = escapeHtml(String(item?.message || ""));
      const payload = escapeHtml(JSON.stringify(item?.payload ?? {}, null, 2));
      const levelClass = ["info", "warn", "error"].includes(level) ? level : "info";
      return `
        <article class="ticket-item db-log-item db-log-item--${levelClass}">
          <div class="ticket-item__meta">#${idx + 1 + Number(state.admin.dbLog.offset || 0)} • ${ts}${ts ? " • " : ""}${source}/${event} ${userId > 0 ? `• user_id=${userId}` : ""}</div>
          <div><span class="db-level-badge db-level-badge--${levelClass}">${escapeHtml(levelClass.toUpperCase())}</span></div>
          <div class="ticket-item__status">${message}</div>
          <details>
            <summary>Payload</summary>
            <pre class="pre-wrap">${payload}</pre>
          </details>
        </article>
      `;
    }).join("");
    setHtml(el.adminDbLogList, html);
  }
  if (el.adminDbMeta) {
    const total = Number(state.admin.dbLog.total || 0);
    const limit = Number(state.admin.dbLog.limit || 0);
    const offset = Number(state.admin.dbLog.offset || 0);
    const from = total > 0 ? offset + 1 : 0;
    const to = Math.min(offset + limit, total);
    el.adminDbMeta.textContent = `Найдено: ${total}. Показано: ${from}-${to}.`;
  }
  if (el.adminDbPrevBtn) {
    el.adminDbPrevBtn.disabled = Number(state.admin.dbLog.offset || 0) <= 0;
  }
  if (el.adminDbNextBtn) {
    const offset = Number(state.admin.dbLog.offset || 0);
    const total = Number(state.admin.dbLog.total || 0);
    const limit = Number(state.admin.dbLog.limit || 0);
    el.adminDbNextBtn.disabled = offset + limit >= total;
  }
}

function applyAdminDbFiltersToUi(filters) {
  if (!filters) return;
  if (el.adminDbSourceFilter && filters.source) el.adminDbSourceFilter.value = String(filters.source);
  if (el.adminDbLevelFilter && filters.level) el.adminDbLevelFilter.value = String(filters.level);
  if (el.adminDbUserIdFilter) el.adminDbUserIdFilter.value = Number(filters.user_id || 0) > 0 ? String(filters.user_id) : "";
  if (el.adminDbSearchFilter) el.adminDbSearchFilter.value = String(filters.search || "");
  if (el.adminDbLimitFilter && filters.limit) el.adminDbLimitFilter.value = String(filters.limit);
  if (el.adminDbAutoRefresh) el.adminDbAutoRefresh.checked = Boolean(filters.auto_refresh);
}

function saveAdminDbFilters(filters) {
  try {
    window.localStorage.setItem(ADMIN_DB_LOG_FILTERS_KEY, JSON.stringify(filters || {}));
  } catch (_e) {
    /* ignore */
  }
}

function restoreAdminDbFilters() {
  try {
    const raw = window.localStorage.getItem(ADMIN_DB_LOG_FILTERS_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") return null;
    return parsed;
  } catch (_e) {
    return null;
  }
}

function stopAdminDbAutoRefresh() {
  if (state.adminDbAutoRefreshTimer) {
    clearInterval(state.adminDbAutoRefreshTimer);
    state.adminDbAutoRefreshTimer = null;
  }
}

function startAdminDbAutoRefresh() {
  stopAdminDbAutoRefresh();
  if (!el.adminDbAutoRefresh?.checked) return;
  state.adminDbAutoRefreshTimer = window.setInterval(() => {
    const panel = document.getElementById("admin-panel-db");
    if (!panel?.classList.contains("is-active")) return;
    const filters = { ...(state.admin.dbLog.lastFilters || readAdminDbLogFilters()) };
    filters.offset = 0;
    void loadAdminDbLog(filters);
  }, 7000);
}

function applyDbPreset(presetId) {
  const preset = String(presetId || "").trim().toLowerCase();
  if (preset === "errors") {
    if (el.adminDbSourceFilter) el.adminDbSourceFilter.value = "all";
    if (el.adminDbLevelFilter) el.adminDbLevelFilter.value = "error";
    if (el.adminDbSearchFilter) el.adminDbSearchFilter.value = "";
  } else if (preset === "payments") {
    if (el.adminDbSourceFilter) el.adminDbSourceFilter.value = "payments";
    if (el.adminDbLevelFilter) el.adminDbLevelFilter.value = "all";
    if (el.adminDbSearchFilter) el.adminDbSearchFilter.value = "";
  } else if (preset === "autopay") {
    if (el.adminDbSourceFilter) el.adminDbSourceFilter.value = "payments";
    if (el.adminDbLevelFilter) el.adminDbLevelFilter.value = "all";
    if (el.adminDbSearchFilter) el.adminDbSearchFilter.value = "autopay";
  } else if (preset === "access") {
    if (el.adminDbSourceFilter) el.adminDbSourceFilter.value = "access";
    if (el.adminDbLevelFilter) el.adminDbLevelFilter.value = "all";
    if (el.adminDbSearchFilter) el.adminDbSearchFilter.value = "";
  } else {
    if (el.adminDbSourceFilter) el.adminDbSourceFilter.value = "all";
    if (el.adminDbLevelFilter) el.adminDbLevelFilter.value = "all";
    if (el.adminDbSearchFilter) el.adminDbSearchFilter.value = "";
  }
  if (el.adminDbLimitFilter && !el.adminDbLimitFilter.value) {
    el.adminDbLimitFilter.value = "100";
  }
}

async function loadAdminDbLog(customFilters = null) {
  const filters = customFilters || readAdminDbLogFilters();
  filters.offset = Math.max(0, Number(filters.offset || 0));
  const qs = new URLSearchParams();
  qs.set("source", String(filters.source || "all"));
  qs.set("level", String(filters.level || "all"));
  if (Number(filters.user_id || 0) > 0) qs.set("user_id", String(Number(filters.user_id)));
  if (String(filters.search || "").trim()) qs.set("search", String(filters.search).trim());
  qs.set("limit", String(Math.max(1, Math.min(Number(filters.limit || 100), 500))));
  qs.set("offset", String(Math.max(0, Number(filters.offset || 0))));

  if (el.adminDbLogList) {
    setHtml(el.adminDbLogList, "<div class='muted'>Загружаем события БД...</div>");
  }
  const data = await api(`/api/admin/db/events?${qs.toString()}`);
  state.admin.dbLog.items = Array.isArray(data?.items) ? data.items : [];
  state.admin.dbLog.total = Number(data?.total || 0);
  state.admin.dbLog.limit = Number(data?.limit || filters.limit || 100);
  state.admin.dbLog.offset = Number(data?.offset || 0);
  state.admin.dbLog.lastFilters = filters;
  saveAdminDbFilters(filters);
  renderAdminDbLogList();
}

function populateAdminTicketsSelect() {
  const tickets = state.admin.tickets || [];
  setHtml(el.adminTicketSelect, "");

  if (tickets.length === 0) {
    const opt = document.createElement("option");
    opt.value = "";
    opt.textContent = "Обращений нет";
    el.adminTicketSelect.appendChild(opt);
    el.adminTicketDetails.textContent = "Обращений нет.";
    return;
  }

  tickets.forEach((ticket) => {
    const opt = document.createElement("option");
    opt.value = String(ticket.id);
    opt.textContent = `#${ticket.id}${ticket.is_premium ? " • PREMIUM" : ""} • ${ticket.is_answered ? "закрыто" : "открыто"}`;
    el.adminTicketSelect.appendChild(opt);
  });

  el.adminTicketSelect.value = String(tickets[0].id);
  syncAdminTicketDetails();
}

function syncAdminTicketDetails() {
  const id = Number(el.adminTicketSelect.value || 0);
  const ticket = (state.admin.tickets || []).find((x) => Number(x.id) === id);
  if (!ticket) {
    el.adminTicketDetails.textContent = "Выберите обращение.";
    return;
  }

  const answer = ticket.answer_text
    ? `<div class="ticket-item__answer"><strong><span class="article-comment__admin-badge">ADMIN</span> Ответ администратора:</strong><br>${escapeHtml(ticket.answer_text)}</div>`
    : "";
  const userBadges = `${ticket.is_admin ? '<span class="article-comment__admin-badge">ADMIN</span>' : ""}${ticket.is_premium ? '<span class="article-comment__premium-badge">PREMIUM</span>' : ""}`;

  setHtml(el.adminTicketDetails, `
    <div class="ticket-item__meta">#${ticket.id} • user ${ticket.user_id} • ${escapeHtml(formatMskDateTimeLabel(ticket.created_at, ""))}</div>
    <div class="ticket-item__status">${ticket.is_answered ? "Закрыто" : "Открыто"}</div>
    <div class="ticket-item__author"><strong>${escapeHtml(ticket.username || `Пользователь #${ticket.user_id}`)}</strong>${userBadges}</div>
    <div class="ticket-item__question">${escapeHtml(ticket.question_text || "")}</div>
    ${answer}
  `);
}

function fillAdminArticle(article = {}) {
  if (!el.adminArticleSelect) return;
  const articleId = Number(article.id || 0);
  el.adminArticleSelect.value = articleId ? String(articleId) : "";
  el.adminArticleTitle.value = article.title || "";
  el.adminArticleSlug.value = article.slug || "";
  el.adminArticleExcerpt.value = article.excerpt || "";
  el.adminArticleCoverPath.value = article.cover_image_path || "";
  if (el.adminArticleCardImagePath) el.adminArticleCardImagePath.value = article.card_image_path || "";
  el.adminArticlePublished.checked = Boolean(article.is_published);
  if (el.adminArticlePremium) el.adminArticlePremium.checked = Boolean(article.is_premium);
  setHtml(el.adminArticleEditor, article.content_html || "");
  state.admin.articleSelectionRange = null;
  setArticleEmojiPickerOpen(false);
  if (article.cover_image_url) {
    el.adminArticleCoverPreview.src = article.cover_image_url;
    el.adminArticleCoverPreview.classList.remove("hidden");
  } else {
    el.adminArticleCoverPreview.classList.add("hidden");
    el.adminArticleCoverPreview.removeAttribute("src");
  }
  if (article.card_image_url && article.card_image_path) {
    el.adminArticleCardImagePreview.src = article.card_image_url;
    el.adminArticleCardImagePreview.classList.remove("hidden");
  } else {
    el.adminArticleCardImagePreview?.classList.add("hidden");
    el.adminArticleCardImagePreview?.removeAttribute("src");
  }
  if (el.adminArticleCoverFile) el.adminArticleCoverFile.value = "";
  if (el.adminArticleCardImageFile) el.adminArticleCardImageFile.value = "";
  if (el.adminArticleInlineImageFile) el.adminArticleInlineImageFile.value = "";
  if (el.adminArticleDeleteBtn) el.adminArticleDeleteBtn.disabled = !articleId;
  setResult(el.adminArticleResult, articleId
    ? `${article.is_published ? "Опубликовано" : "Черновик"} · обновлено ${formatMskDateTimeLabel(article.updated_at, "")}`
    : "Новая статья ещё не сохранена.");
}

function populateAdminArticleSelect(selectedId = 0) {
  if (!el.adminArticleSelect) return;
  setHtml(el.adminArticleSelect, "");
  appendAdminCreateOption(el.adminArticleSelect, "+ Новая статья");
  (state.admin.articles || []).forEach((article) => {
    const option = document.createElement("option");
    option.value = String(article.id || 0);
    option.textContent = `${article.is_published ? "●" : "○"} ${article.title || "Без заголовка"}`;
    el.adminArticleSelect.appendChild(option);
  });
  const safeSelected = Number(selectedId || 0);
  if (safeSelected && (state.admin.articles || []).some((item) => Number(item.id) === safeSelected)) {
    el.adminArticleSelect.value = String(safeSelected);
    fillAdminArticle(state.admin.articles.find((item) => Number(item.id) === safeSelected));
  } else {
    el.adminArticleSelect.value = "";
    fillAdminArticle({});
  }
}

function selectedAdminArticle() {
  const articleId = Number(el.adminArticleSelect?.value || 0);
  return (state.admin.articles || []).find((item) => Number(item.id) === articleId) || null;
}

function rememberArticleEditorSelection() {
  if (!el.adminArticleEditor) return;
  const selection = window.getSelection?.();
  if (!selection || selection.rangeCount < 1) return;
  const range = selection.getRangeAt(0);
  const container = range.commonAncestorContainer.nodeType === Node.ELEMENT_NODE
    ? range.commonAncestorContainer
    : range.commonAncestorContainer.parentElement;
  if (container && el.adminArticleEditor.contains(container)) {
    state.admin.articleSelectionRange = range.cloneRange();
  }
}

function restoreArticleEditorSelection() {
  if (!el.adminArticleEditor) return;
  el.adminArticleEditor.focus();
  const selection = window.getSelection?.();
  const range = state.admin.articleSelectionRange;
  if (!selection || !range) return;
  try {
    selection.removeAllRanges();
    selection.addRange(range);
  } catch (_error) {
    state.admin.articleSelectionRange = null;
  }
}

function runArticleEditorCommand(command, value = null) {
  if (!el.adminArticleEditor) return;
  restoreArticleEditorSelection();
  document.execCommand(command, false, value);
  rememberArticleEditorSelection();
}

function setArticleEmojiPickerOpen(isOpen) {
  const open = Boolean(isOpen && el.adminArticleEmojiPanel && el.adminArticleEmojiBtn);
  if (el.adminArticleEmojiPanel) el.adminArticleEmojiPanel.hidden = !open;
  if (el.adminArticleEmojiBtn) el.adminArticleEmojiBtn.setAttribute("aria-expanded", String(open));
  el.adminArticleToolbar?.classList.toggle("has-open-emoji-panel", open);
}

function insertArticleEditorEmoji(rawEmoji) {
  const emoji = String(rawEmoji || "");
  if (!emoji || !el.adminArticleEditor) return;
  restoreArticleEditorSelection();
  const currentSelection = window.getSelection?.();
  const currentRange = currentSelection?.rangeCount ? currentSelection.getRangeAt(0) : null;
  const currentContainer = currentRange?.commonAncestorContainer?.nodeType === Node.ELEMENT_NODE
    ? currentRange.commonAncestorContainer
    : currentRange?.commonAncestorContainer?.parentElement;
  if (!currentRange || !currentContainer || !el.adminArticleEditor.contains(currentContainer)) {
    const endRange = document.createRange();
    endRange.selectNodeContents(el.adminArticleEditor);
    endRange.collapse(false);
    currentSelection?.removeAllRanges();
    currentSelection?.addRange(endRange);
  }
  const insertedWithUndo = document.execCommand("insertText", false, emoji);
  if (!insertedWithUndo) {
    const selection = window.getSelection?.();
    let range = selection?.rangeCount ? selection.getRangeAt(0) : null;
    const rangeContainer = range?.commonAncestorContainer?.nodeType === Node.ELEMENT_NODE
      ? range.commonAncestorContainer
      : range?.commonAncestorContainer?.parentElement;
    if (!range || !rangeContainer || !el.adminArticleEditor.contains(rangeContainer)) {
      range = document.createRange();
      range.selectNodeContents(el.adminArticleEditor);
      range.collapse(false);
    }
    const emojiNode = document.createTextNode(emoji);
    range.deleteContents();
    range.insertNode(emojiNode);
    range.setStartAfter(emojiNode);
    range.collapse(true);
    selection?.removeAllRanges();
    selection?.addRange(range);
    el.adminArticleEditor.dispatchEvent(new InputEvent("input", { bubbles: true, inputType: "insertText", data: emoji }));
  }
  rememberArticleEditorSelection();
  setArticleEmojiPickerOpen(false);
  setResult(el.adminArticleResult, "Эмодзи вставлен. Нажмите «Сохранить».");
}

async function uploadAdminArticleImage(file) {
  if (!file) throw new Error("Выберите изображение.");
  const body = new FormData();
  body.append("file", file);
  return api("/api/admin/articles/image", {
    method: "POST",
    body,
    isFormData: true,
  });
}

const photoEditorState = {
  sourceImg: null,
  sourceMimeType: "image/jpeg",
  originalSrc: "",
  originalObjectUrl: "",
  targetPreset: "cover",
  onSave: null,
  outputWidth: 1280,
  outputMimeType: "image/jpeg",
  outputQuality: 0.98,
  resizeMode: "crop",

  zoom: 1,
  panX: 0,
  panY: 0,
  rotation: 0,
  flipH: false,
  flipV: false,

  brightness: 0,
  contrast: 0,
  saturation: 0,
  blur: 0,

  isCropActive: false,
  isDragging: false,
  startX: 0,
  startY: 0,
  initialPanX: 0,
  initialPanY: 0,
};

const PHOTO_EDITOR_TARGETS = Object.freeze({
  cover: {
    label: "Обложка статьи",
    selector: ".article-cover",
    ratio: 16 / 9,
    ratioLabel: "16:9",
    outputWidth: 1280,
  },
  avatar: {
    label: "Аватарка карточки",
    selector: ".article-list-card__image",
    ratio: 1,
    ratioLabel: "1:1",
    outputWidth: 512,
  },
  inline: {
    label: "Фото в тексте",
    selector: ".article-content img",
    ratio: 4 / 3,
    ratioLabel: "4:3",
    outputWidth: 1200,
  },
});

function visibleElementRect(selector) {
  const candidates = Array.from(document.querySelectorAll(selector));
  for (const candidate of candidates) {
    const rect = candidate.getBoundingClientRect();
    if (rect.width > 1 && rect.height > 1) return rect;
  }
  return null;
}

function cssAspectRatio(selector, fallbackRatio) {
  const candidate = document.querySelector(selector);
  if (!candidate) return fallbackRatio;
  const raw = String(getComputedStyle(candidate).aspectRatio || "").trim();
  const match = raw.match(/^([\d.]+)\s*\/\s*([\d.]+)$/);
  if (!match) return fallbackRatio;
  const width = Number(match[1]);
  const height = Number(match[2]);
  return width > 0 && height > 0 ? width / height : fallbackRatio;
}

function getPhotoEditorTargetInfo(presetKey = "cover") {
  const config = PHOTO_EDITOR_TARGETS[presetKey] || PHOTO_EDITOR_TARGETS.cover;
  const renderedRect = visibleElementRect(config.selector);
  const ratio = renderedRect
    ? renderedRect.width / renderedRect.height
    : cssAspectRatio(config.selector, config.ratio);
  const configuredOutputWidth = presetKey === photoEditorState.targetPreset
    ? Number(photoEditorState.outputWidth || config.outputWidth)
    : config.outputWidth;
  const outputWidth = Math.min(3840, Math.max(256, Math.round(configuredOutputWidth)));
  const outputHeight = Math.round(outputWidth / ratio);
  const renderedLabel = renderedRect
    ? ` · сейчас в интерфейсе ${Math.round(renderedRect.width)}×${Math.round(renderedRect.height)} px`
    : "";
  return {
    ratio,
    ratioLabel: config.ratioLabel,
    outputWidth,
    outputHeight,
    label: `${config.label} · пользовательский формат ${config.ratioLabel}${renderedLabel} · файл ${outputWidth}×${outputHeight} px`,
  };
}

function photoEditorMimeTypeFromSource(file, url = "") {
  const fileType = String(file?.type || "").toLowerCase();
  if (["image/png", "image/webp", "image/jpeg"].includes(fileType)) return fileType;
  const cleanUrl = String(url || "").split(/[?#]/, 1)[0].toLowerCase();
  if (cleanUrl.endsWith(".png")) return "image/png";
  if (cleanUrl.endsWith(".webp")) return "image/webp";
  return "image/jpeg";
}

function resetPhotoEditorOutputSettings() {
  const config = PHOTO_EDITOR_TARGETS[photoEditorState.targetPreset] || PHOTO_EDITOR_TARGETS.cover;
  photoEditorState.outputWidth = config.outputWidth;
  photoEditorState.outputMimeType = photoEditorState.sourceMimeType === "image/png"
    ? "image/png"
    : photoEditorState.sourceMimeType === "image/webp"
      ? "image/webp"
      : "image/jpeg";
  photoEditorState.outputQuality = 0.98;
}

function formatPhotoEditorValue(value, digits = 1) {
  const rounded = Number(Number(value || 0).toFixed(digits));
  return String(Object.is(rounded, -0) ? 0 : rounded);
}

function isPhotoEditorStretchMode() {
  return photoEditorState.resizeMode === "stretch";
}

function syncPhotoEditorControls() {
  if (el.photoEditorZoomValue) {
    el.photoEditorZoomValue.value = `${Math.round(Number(photoEditorState.zoom || 1) * 100)}%`;
  }
  if (el.photoEditorBrightnessValue) {
    el.photoEditorBrightnessValue.value = formatPhotoEditorValue(photoEditorState.brightness);
  }
  if (el.photoEditorContrastValue) {
    el.photoEditorContrastValue.value = formatPhotoEditorValue(photoEditorState.contrast);
  }
  if (el.photoEditorSaturationValue) {
    el.photoEditorSaturationValue.value = formatPhotoEditorValue(photoEditorState.saturation);
  }
  if (el.photoEditorBlurValue) {
    el.photoEditorBlurValue.value = `${formatPhotoEditorValue(photoEditorState.blur, 2)} px`;
  }

  const targetInfo = getPhotoEditorTargetInfo(photoEditorState.targetPreset);
  if (el.photoEditorOutputWidth) el.photoEditorOutputWidth.value = String(targetInfo.outputWidth);
  if (el.photoEditorOutputHeight) el.photoEditorOutputHeight.value = String(targetInfo.outputHeight);
  if (el.photoEditorOutputFormat) el.photoEditorOutputFormat.value = photoEditorState.outputMimeType;
  if (el.photoEditorOutputQuality) el.photoEditorOutputQuality.value = String(photoEditorState.outputQuality);
  if (el.photoEditorOutputQualityValue) {
    el.photoEditorOutputQualityValue.value = `${Math.round(photoEditorState.outputQuality * 100)}%`;
  }

  const isLossless = photoEditorState.outputMimeType === "image/png";
  if (el.photoEditorOutputQuality) el.photoEditorOutputQuality.disabled = isLossless;
  el.photoEditorQualityField?.classList.toggle("is-disabled", isLossless);
  if (el.photoEditorOutputHint) {
    el.photoEditorOutputHint.textContent = isLossless
      ? "PNG сохраняется без потерь; настройка качества для него не требуется."
      : "Высокое значение лучше сохраняет мелкий текст и детали, но увеличивает размер файла.";
  }

  const isStretch = isPhotoEditorStretchMode();
  if (el.photoEditorResizeMode) el.photoEditorResizeMode.value = photoEditorState.resizeMode;
  if (el.photoEditorResizeModeHint) {
    el.photoEditorResizeModeHint.textContent = isStretch
      ? "Всё изображение будет растянуто точно по рамке без обрезания. Пропорции фотографии изменятся."
      : "Изображение не подгоняется автоматически. Перетащите его и вручную настройте масштаб внутри рамки.";
  }
  if (el.photoEditorZoomSlider) el.photoEditorZoomSlider.disabled = isStretch;
  el.photoEditorZoomField?.classList.toggle("is-disabled", isStretch);
  if (el.photoEditorToggleCropBtn) el.photoEditorToggleCropBtn.disabled = isStretch;
  el.photoEditorViewport?.classList.toggle("is-stretch-mode", isStretch);
}

function getPhotoEditorCropRect() {
  const vpRect = el.photoEditorViewport?.getBoundingClientRect() || { width: 500, height: 360 };
  const targetInfo = getPhotoEditorTargetInfo(photoEditorState.targetPreset || "cover");
  const padding = 24;
  const viewportWidth = vpRect.width || 500;
  const viewportHeight = vpRect.height || 360;
  const availW = Math.max(100, viewportWidth - padding * 2);
  const availH = Math.max(100, viewportHeight - padding * 2);
  let width;
  let height;
  if (availW / availH > targetInfo.ratio) {
    height = availH;
    width = height * targetInfo.ratio;
  } else {
    width = availW;
    height = width / targetInfo.ratio;
  }
  return {
    left: (viewportWidth - width) / 2,
    top: (viewportHeight - height) / 2,
    width,
    height,
    targetInfo,
  };
}

function updatePhotoEditorTargetOptionLabels() {
  if (!el.photoEditorTargetPreset) return;
  Array.from(el.photoEditorTargetPreset.options).forEach((option) => {
    const info = getPhotoEditorTargetInfo(option.value);
    option.textContent = info.label;
  });
}

function updatePhotoEditorCropOverlayShape() {
  if (!el.photoEditorCropOverlay || !el.photoEditorViewport || !el.photoEditorCropFrame) return;
  const cropRect = getPhotoEditorCropRect();

  if (el.photoEditorCropFrameLabel) {
    el.photoEditorCropFrameLabel.textContent = isPhotoEditorStretchMode()
      ? `Растягивание без обрезания · ${cropRect.targetInfo.label}`
      : cropRect.targetInfo.label;
  }
  el.photoEditorCropFrame.style.width = `${Math.round(cropRect.width)}px`;
  el.photoEditorCropFrame.style.height = `${Math.round(cropRect.height)}px`;
}

function getPhotoEditorBaseDrawSize(img, viewportWidth, viewportHeight) {
  const imgWidth = img.naturalWidth || img.width;
  const imgHeight = img.naturalHeight || img.height;
  const imgAspect = imgWidth / (imgHeight || 1);
  const vpAspect = viewportWidth / (viewportHeight || 1);
  if (imgAspect > vpAspect) {
    const width = viewportWidth * 0.85;
    return { width, height: width / imgAspect };
  }
  const height = viewportHeight * 0.85;
  return { width: height * imgAspect, height };
}

function configurePhotoEditorDrawingContext(ctx, filterScale = 1) {
  const b = 100 + Number(photoEditorState.brightness || 0);
  const c = 100 + Number(photoEditorState.contrast || 0);
  const s = 100 + Number(photoEditorState.saturation || 0);
  const blurPx = Number(photoEditorState.blur || 0) * Math.max(0.01, Number(filterScale || 1));
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = "high";
  ctx.filter = `brightness(${b}%) contrast(${c}%) saturate(${s}%) blur(${blurPx}px)`;
}

function drawPhotoEditorSource(ctx, viewportWidth, viewportHeight, { filterScale = 1 } = {}) {
  const img = photoEditorState.sourceImg;
  if (!ctx || !img) return;

  const centerX = viewportWidth / 2 + photoEditorState.panX;
  const centerY = viewportHeight / 2 + photoEditorState.panY;
  const baseDrawSize = getPhotoEditorBaseDrawSize(img, viewportWidth, viewportHeight);

  ctx.save();
  configurePhotoEditorDrawingContext(ctx, filterScale);
  ctx.translate(centerX, centerY);
  ctx.rotate((photoEditorState.rotation * Math.PI) / 180);
  ctx.scale(
    photoEditorState.flipH ? -photoEditorState.zoom : photoEditorState.zoom,
    photoEditorState.flipV ? -photoEditorState.zoom : photoEditorState.zoom,
  );
  ctx.drawImage(
    img,
    -baseDrawSize.width / 2,
    -baseDrawSize.height / 2,
    baseDrawSize.width,
    baseDrawSize.height,
  );
  ctx.restore();
}

function drawPhotoEditorStretchedSource(ctx, frameRect, { filterScale = 1 } = {}) {
  const img = photoEditorState.sourceImg;
  if (!ctx || !img || !frameRect) return;

  const rotation = ((photoEditorState.rotation % 360) + 360) % 360;
  const swapsAxes = rotation === 90 || rotation === 270;
  const drawWidth = swapsAxes ? frameRect.height : frameRect.width;
  const drawHeight = swapsAxes ? frameRect.width : frameRect.height;

  ctx.save();
  configurePhotoEditorDrawingContext(ctx, filterScale);
  ctx.translate(frameRect.left + frameRect.width / 2, frameRect.top + frameRect.height / 2);
  ctx.rotate((rotation * Math.PI) / 180);
  ctx.scale(photoEditorState.flipH ? -1 : 1, photoEditorState.flipV ? -1 : 1);
  ctx.drawImage(img, -drawWidth / 2, -drawHeight / 2, drawWidth, drawHeight);
  ctx.restore();
}

function setPhotoEditorCropActive(isActive) {
  const isStretch = isPhotoEditorStretchMode();
  photoEditorState.isCropActive = isStretch || Boolean(isActive);
  el.photoEditorCropOverlay?.classList.toggle("hidden", !photoEditorState.isCropActive);
  el.photoEditorToggleCropBtn?.classList.toggle("is-active", photoEditorState.isCropActive && !isStretch);
  el.photoEditorApplyCropBtn?.classList.toggle("hidden", !photoEditorState.isCropActive || isStretch);
  if (photoEditorState.isCropActive) updatePhotoEditorCropOverlayShape();
}

function renderPhotoEditorCanvas() {
  const canvas = el.photoEditorCanvas;
  const ctx = canvas?.getContext("2d");
  const img = photoEditorState.sourceImg;
  if (!canvas || !ctx || !img) return;

  const vpRect = el.photoEditorViewport?.getBoundingClientRect() || { width: 600, height: 400 };
  const dpr = window.devicePixelRatio || 1;
  const cssWidth = vpRect.width || 600;
  const cssHeight = vpRect.height || 400;

  canvas.width = Math.round(cssWidth * dpr);
  canvas.height = Math.round(cssHeight * dpr);
  canvas.style.width = `${cssWidth}px`;
  canvas.style.height = `${cssHeight}px`;

  ctx.save();
  ctx.scale(dpr, dpr);
  ctx.clearRect(0, 0, cssWidth, cssHeight);

  ctx.fillStyle = "#111625";
  ctx.fillRect(0, 0, cssWidth, cssHeight);
  if (isPhotoEditorStretchMode()) {
    drawPhotoEditorStretchedSource(ctx, getPhotoEditorCropRect());
  } else {
    drawPhotoEditorSource(ctx, cssWidth, cssHeight);
  }
  ctx.restore();

  if (photoEditorState.isCropActive) {
    updatePhotoEditorCropOverlayShape();
  }
}

function resetPhotoEditorState({ activateCrop = false } = {}) {
  photoEditorState.zoom = 1;
  photoEditorState.panX = 0;
  photoEditorState.panY = 0;
  photoEditorState.rotation = 0;
  photoEditorState.flipH = false;
  photoEditorState.flipV = false;
  photoEditorState.brightness = 0;
  photoEditorState.contrast = 0;
  photoEditorState.saturation = 0;
  photoEditorState.blur = 0;
  photoEditorState.isCropActive = false;

  if (el.photoEditorZoomSlider) el.photoEditorZoomSlider.value = "1";
  if (el.photoEditorBrightness) el.photoEditorBrightness.value = "0";
  if (el.photoEditorContrast) el.photoEditorContrast.value = "0";
  if (el.photoEditorSaturation) el.photoEditorSaturation.value = "0";
  if (el.photoEditorBlur) el.photoEditorBlur.value = "0";

  setPhotoEditorCropActive(activateCrop);
  syncPhotoEditorControls();

  renderPhotoEditorCanvas();
}

function centerAppliedPhotoEditorCrop() {
  const img = photoEditorState.sourceImg;
  if (!img || !el.photoEditorViewport) return;
  const viewportRect = el.photoEditorViewport.getBoundingClientRect();
  const viewportWidth = viewportRect.width || 600;
  const viewportHeight = viewportRect.height || 400;
  const baseDrawSize = getPhotoEditorBaseDrawSize(img, viewportWidth, viewportHeight);
  const cropRect = getPhotoEditorCropRect();
  photoEditorState.zoom = Math.min(8, Math.max(
    0.1,
    cropRect.width / Math.max(1, baseDrawSize.width),
    cropRect.height / Math.max(1, baseDrawSize.height),
  ));
  photoEditorState.panX = 0;
  photoEditorState.panY = 0;
  if (el.photoEditorZoomSlider) el.photoEditorZoomSlider.value = String(photoEditorState.zoom);
  syncPhotoEditorControls();
  renderPhotoEditorCanvas();
}

function renderPhotoEditorTargetCanvas() {
  if (!photoEditorState.sourceImg || !el.photoEditorViewport) return null;
  const viewportRect = el.photoEditorViewport.getBoundingClientRect();
  const viewportWidth = viewportRect.width || 600;
  const viewportHeight = viewportRect.height || 400;
  const cropRect = getPhotoEditorCropRect();
  const targetCanvas = document.createElement("canvas");
  targetCanvas.width = cropRect.targetInfo.outputWidth;
  targetCanvas.height = cropRect.targetInfo.outputHeight;
  const targetCtx = targetCanvas.getContext("2d");
  if (!targetCtx) return null;

  const scaleX = targetCanvas.width / cropRect.width;
  const scaleY = targetCanvas.height / cropRect.height;
  targetCtx.imageSmoothingEnabled = true;
  targetCtx.imageSmoothingQuality = "high";
  targetCtx.fillStyle = "#111625";
  targetCtx.fillRect(0, 0, targetCanvas.width, targetCanvas.height);
  if (isPhotoEditorStretchMode()) {
    drawPhotoEditorStretchedSource(
      targetCtx,
      { left: 0, top: 0, width: targetCanvas.width, height: targetCanvas.height },
      { filterScale: Math.max(scaleX, scaleY) },
    );
  } else {
    targetCtx.save();
    targetCtx.scale(scaleX, scaleY);
    targetCtx.translate(-cropRect.left, -cropRect.top);
    drawPhotoEditorSource(targetCtx, viewportWidth, viewportHeight, {
      filterScale: Math.max(scaleX, scaleY),
    });
    targetCtx.restore();
  }
  return targetCanvas;
}

function photoEditorCanvasToBlob(canvas, mimeType, quality) {
  return new Promise((resolve) => canvas.toBlob(resolve, mimeType, quality));
}

function photoEditorExtension(mimeType) {
  if (mimeType === "image/png") return "png";
  if (mimeType === "image/webp") return "webp";
  return "jpg";
}

async function applyPhotoEditorCrop() {
  if (isPhotoEditorStretchMode()) return;
  const targetCanvas = renderPhotoEditorTargetCanvas();
  if (!targetCanvas) return;
  const croppedBlob = await photoEditorCanvasToBlob(targetCanvas, "image/png", 1);
  if (!croppedBlob) throw new Error("Не удалось сформировать изображение.");
  const croppedObjectUrl = URL.createObjectURL(croppedBlob);
  const newImg = new Image();
  try {
    await new Promise((resolve, reject) => {
      newImg.onload = resolve;
      newImg.onerror = reject;
      newImg.src = croppedObjectUrl;
    });
  } finally {
    URL.revokeObjectURL(croppedObjectUrl);
  }
  photoEditorState.sourceImg = newImg;
  photoEditorState.sourceMimeType = "image/png";
  resetPhotoEditorState({ activateCrop: true });
  centerAppliedPhotoEditorCrop();
  showToast("Кадрирование применено без потери качества.");
}

window.openArticlePhotoEditor = openArticlePhotoEditor;

async function openArticlePhotoEditor({ file, url, targetContext = "cover", onSave = null }) {
  if (!el.articlePhotoEditorDialog) return;

  if (photoEditorState.originalObjectUrl) {
    URL.revokeObjectURL(photoEditorState.originalObjectUrl);
    photoEditorState.originalObjectUrl = "";
  }
  photoEditorState.targetPreset = targetContext;
  photoEditorState.onSave = onSave;
  photoEditorState.resizeMode = "crop";
  photoEditorState.sourceMimeType = photoEditorMimeTypeFromSource(file, url);
  resetPhotoEditorOutputSettings();
  if (el.photoEditorTargetPreset) el.photoEditorTargetPreset.value = targetContext;

  const loadSrc = (src) => {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.crossOrigin = "anonymous";
      img.onload = () => resolve(img);
      img.onerror = () => reject(new Error("Не удалось загрузить изображение для редактирования."));
      img.src = src;
    });
  };

  try {
    let imageSrc = "";
    if (file) {
      imageSrc = URL.createObjectURL(file);
      photoEditorState.originalObjectUrl = imageSrc;
    } else if (url) {
      imageSrc = url;
    } else {
      throw new Error("Выберите файл изображения или укажите адрес.");
    }

    const loadedImg = await loadSrc(imageSrc);
    photoEditorState.sourceImg = loadedImg;
    photoEditorState.originalSrc = imageSrc;
    const defaultWidth = PHOTO_EDITOR_TARGETS[targetContext]?.outputWidth || 1280;
    const sourceWidth = loadedImg.naturalWidth || loadedImg.width || defaultWidth;
    photoEditorState.outputWidth = Math.max(256, Math.min(defaultWidth, sourceWidth));

    el.articlePhotoEditorDialog.showModal();
    updatePhotoEditorTargetOptionLabels();

    requestAnimationFrame(() => {
      resetPhotoEditorState({ activateCrop: true });
    });
  } catch (err) {
    if (photoEditorState.originalObjectUrl) {
      URL.revokeObjectURL(photoEditorState.originalObjectUrl);
      photoEditorState.originalObjectUrl = "";
    }
    showToast(errorText(err, "Ошибка загрузки изображения"), true);
  }
}

async function saveArticlePhotoEditor() {
  if (!photoEditorState.sourceImg || !el.photoEditorCanvas) return;
  setButtonBusy(el.photoEditorSaveBtn, true, "Сохраняем…");

  try {
    const exportCanvas = renderPhotoEditorTargetCanvas();
    if (!exportCanvas) throw new Error("Не удалось сформировать изображение.");
    const requestedMimeType = photoEditorState.outputMimeType || "image/jpeg";
    const blob = await photoEditorCanvasToBlob(
      exportCanvas,
      requestedMimeType,
      photoEditorState.outputQuality,
    );
    if (!blob) throw new Error("Не удалось сформировать изображение.");

    const actualMimeType = blob.type || requestedMimeType;
    const extension = photoEditorExtension(actualMimeType);
    const editedFile = new File(
      [blob],
      `article_photo_${Date.now()}.${extension}`,
      { type: actualMimeType },
    );
    const uploaded = await uploadAdminArticleImage(editedFile);

    if (photoEditorState.onSave) {
      await photoEditorState.onSave(uploaded, editedFile, photoEditorState.targetPreset);
    }

    el.articlePhotoEditorDialog?.close();
    showToast("Изображение сохранено и привязано.");
  } catch (err) {
    showToast(errorText(err, "Не удалось сохранить отредактированное изображение."), true);
  } finally {
    setButtonBusy(el.photoEditorSaveBtn, false);
  }
}

function setupArticlePhotoEditorEvents() {
  if (!el.articlePhotoEditorDialog) return;

  el.photoEditorTargetPreset?.addEventListener("change", async () => {
    photoEditorState.targetPreset = el.photoEditorTargetPreset.value;
    resetPhotoEditorOutputSettings();
    if (photoEditorState.originalSrc) {
      try {
        const img = new Image();
        img.crossOrigin = "anonymous";
        await new Promise((resolve, reject) => {
          img.onload = resolve;
          img.onerror = reject;
          img.src = photoEditorState.originalSrc;
        });
        photoEditorState.sourceImg = img;
      } catch (err) {
        console.warn("Could not reload original image on target preset change", err);
      }
    }
    updatePhotoEditorTargetOptionLabels();
    resetPhotoEditorState({ activateCrop: true });
  });

  const tabWrap = el.articlePhotoEditorDialog.querySelector(".photo-editor-tabs");
  if (tabWrap) {
    tabWrap.querySelectorAll("[data-editor-tab]").forEach((btn) => {
      btn.addEventListener("click", () => {
        tabWrap.querySelectorAll("[data-editor-tab]").forEach((b) => b.classList.remove("is-active"));
        btn.classList.add("is-active");
        const tabKey = btn.dataset.editorTab;
        const panelMap = {
          crop: "editorPanelCrop",
          adjust: "editorPanelAdjust",
          transform: "editorPanelTransform",
          filters: "editorPanelFilters",
          output: "editorPanelOutput",
        };
        Object.entries(panelMap).forEach(([k, id]) => {
          const panel = document.getElementById(id);
          if (panel) panel.classList.toggle("hidden", k !== tabKey);
        });
      });
    });
  }

  el.photoEditorToggleCropBtn?.addEventListener("click", () => {
    setPhotoEditorCropActive(!photoEditorState.isCropActive);
  });

  el.photoEditorResizeMode?.addEventListener("change", () => {
    photoEditorState.resizeMode = el.photoEditorResizeMode.value === "stretch" ? "stretch" : "crop";
    photoEditorState.zoom = 1;
    photoEditorState.panX = 0;
    photoEditorState.panY = 0;
    if (el.photoEditorZoomSlider) el.photoEditorZoomSlider.value = "1";
    setPhotoEditorCropActive(true);
    syncPhotoEditorControls();
    renderPhotoEditorCanvas();
  });

  el.photoEditorApplyCropBtn?.addEventListener("click", () => {
    void applyPhotoEditorCrop().catch((error) => {
      showToast(errorText(error, "Не удалось применить кадрирование."), true);
    });
  });

  el.photoEditorZoomSlider?.addEventListener("input", () => {
    photoEditorState.zoom = parseFloat(el.photoEditorZoomSlider.value || "1");
    syncPhotoEditorControls();
    renderPhotoEditorCanvas();
  });

  el.photoEditorBrightness?.addEventListener("input", () => {
    photoEditorState.brightness = parseFloat(el.photoEditorBrightness.value || "0");
    syncPhotoEditorControls();
    renderPhotoEditorCanvas();
  });
  el.photoEditorContrast?.addEventListener("input", () => {
    photoEditorState.contrast = parseFloat(el.photoEditorContrast.value || "0");
    syncPhotoEditorControls();
    renderPhotoEditorCanvas();
  });
  el.photoEditorSaturation?.addEventListener("input", () => {
    photoEditorState.saturation = parseFloat(el.photoEditorSaturation.value || "0");
    syncPhotoEditorControls();
    renderPhotoEditorCanvas();
  });
  el.photoEditorBlur?.addEventListener("input", () => {
    photoEditorState.blur = parseFloat(el.photoEditorBlur.value || "0");
    syncPhotoEditorControls();
    renderPhotoEditorCanvas();
  });

  el.photoEditorOutputWidth?.addEventListener("input", () => {
    const requestedWidth = Math.round(Number(el.photoEditorOutputWidth.value || 0));
    if (requestedWidth >= 256 && requestedWidth <= 3840) {
      photoEditorState.outputWidth = requestedWidth;
      syncPhotoEditorControls();
      updatePhotoEditorCropOverlayShape();
    }
  });
  el.photoEditorOutputWidth?.addEventListener("change", () => {
    const requestedWidth = Math.round(Number(el.photoEditorOutputWidth.value || 0));
    photoEditorState.outputWidth = Math.min(3840, Math.max(256, requestedWidth || 1280));
    syncPhotoEditorControls();
    updatePhotoEditorTargetOptionLabels();
  });
  el.photoEditorOutputFormat?.addEventListener("change", () => {
    photoEditorState.outputMimeType = el.photoEditorOutputFormat.value || "image/jpeg";
    syncPhotoEditorControls();
  });
  el.photoEditorOutputQuality?.addEventListener("input", () => {
    photoEditorState.outputQuality = Math.min(
      1,
      Math.max(0.8, parseFloat(el.photoEditorOutputQuality.value || "0.98")),
    );
    syncPhotoEditorControls();
  });

  el.photoEditorRotateCcwBtn?.addEventListener("click", () => {
    photoEditorState.rotation = (photoEditorState.rotation - 90 + 360) % 360;
    renderPhotoEditorCanvas();
  });
  el.photoEditorRotateCwBtn?.addEventListener("click", () => {
    photoEditorState.rotation = (photoEditorState.rotation + 90) % 360;
    renderPhotoEditorCanvas();
  });
  el.photoEditorFlipHBtn?.addEventListener("click", () => {
    photoEditorState.flipH = !photoEditorState.flipH;
    renderPhotoEditorCanvas();
  });
  el.photoEditorFlipVBtn?.addEventListener("click", () => {
    photoEditorState.flipV = !photoEditorState.flipV;
    renderPhotoEditorCanvas();
  });

  el.articlePhotoEditorDialog.querySelectorAll("[data-filter-preset]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const preset = btn.dataset.filterPreset;
      if (preset === "normal") {
        photoEditorState.brightness = 0;
        photoEditorState.contrast = 0;
        photoEditorState.saturation = 0;
        photoEditorState.blur = 0;
      } else if (preset === "grayscale") {
        photoEditorState.saturation = -100;
        photoEditorState.contrast = 10;
      } else if (preset === "sepia") {
        photoEditorState.saturation = -30;
        photoEditorState.brightness = 10;
        photoEditorState.contrast = 15;
      } else if (preset === "vibrant") {
        photoEditorState.saturation = 40;
        photoEditorState.contrast = 20;
      } else if (preset === "warm") {
        photoEditorState.saturation = 15;
        photoEditorState.brightness = 5;
        photoEditorState.contrast = -5;
      }
      if (el.photoEditorBrightness) el.photoEditorBrightness.value = String(photoEditorState.brightness);
      if (el.photoEditorContrast) el.photoEditorContrast.value = String(photoEditorState.contrast);
      if (el.photoEditorSaturation) el.photoEditorSaturation.value = String(photoEditorState.saturation);
      if (el.photoEditorBlur) el.photoEditorBlur.value = String(photoEditorState.blur);
      syncPhotoEditorControls();
      renderPhotoEditorCanvas();
    });
  });

  el.photoEditorResetBtn?.addEventListener("click", () => {
    photoEditorState.resizeMode = "crop";
    resetPhotoEditorOutputSettings();
    resetPhotoEditorState({ activateCrop: true });
    updatePhotoEditorTargetOptionLabels();
  });
  el.photoEditorSaveBtn?.addEventListener("click", saveArticlePhotoEditor);

  const viewport = el.photoEditorViewport;
  if (viewport) {
    const startDrag = (clientX, clientY) => {
      if (isPhotoEditorStretchMode()) return;
      photoEditorState.isDragging = true;
      photoEditorState.startX = clientX;
      photoEditorState.startY = clientY;
      photoEditorState.initialPanX = photoEditorState.panX;
      photoEditorState.initialPanY = photoEditorState.panY;
    };

    const moveDrag = (clientX, clientY) => {
      if (!photoEditorState.isDragging || isPhotoEditorStretchMode()) return;
      const dx = clientX - photoEditorState.startX;
      const dy = clientY - photoEditorState.startY;
      photoEditorState.panX = photoEditorState.initialPanX + dx;
      photoEditorState.panY = photoEditorState.initialPanY + dy;
      renderPhotoEditorCanvas();
    };

    const endDrag = () => {
      photoEditorState.isDragging = false;
    };

    viewport.addEventListener("mousedown", (e) => startDrag(e.clientX, e.clientY));
    window.addEventListener("mousemove", (e) => moveDrag(e.clientX, e.clientY));
    window.addEventListener("mouseup", endDrag);

    viewport.addEventListener("touchstart", (e) => {
      if (e.touches.length === 1) {
        startDrag(e.touches[0].clientX, e.touches[0].clientY);
      }
    });
    window.addEventListener("touchmove", (e) => {
      if (e.touches.length === 1) {
        moveDrag(e.touches[0].clientX, e.touches[0].clientY);
      }
    });
    window.addEventListener("touchend", endDrag);

    viewport.addEventListener("wheel", (event) => {
      if (isPhotoEditorStretchMode()) return;
      event.preventDefault();
      const zoomFactor = Math.exp(-event.deltaY * 0.001);
      photoEditorState.zoom = Math.min(8, Math.max(0.1, photoEditorState.zoom * zoomFactor));
      if (el.photoEditorZoomSlider) el.photoEditorZoomSlider.value = String(photoEditorState.zoom);
      syncPhotoEditorControls();
      renderPhotoEditorCanvas();
    }, { passive: false });
  }

  el.articlePhotoEditorDialog.addEventListener("close", () => {
    if (photoEditorState.originalObjectUrl) {
      URL.revokeObjectURL(photoEditorState.originalObjectUrl);
    }
    photoEditorState.originalObjectUrl = "";
    photoEditorState.originalSrc = "";
    photoEditorState.sourceImg = null;
    photoEditorState.onSave = null;
    photoEditorState.isDragging = false;
  });
}

async function saveAdminArticle() {
  const selected = selectedAdminArticle();
  const body = {
    title: String(el.adminArticleTitle?.value || "").trim(),
    slug: String(el.adminArticleSlug?.value || "").trim(),
    excerpt: String(el.adminArticleExcerpt?.value || "").trim(),
    cover_image_path: String(el.adminArticleCoverPath?.value || "").trim(),
    card_image_path: String(el.adminArticleCardImagePath?.value || "").trim(),
    content_html: String(el.adminArticleEditor?.innerHTML || "").trim(),
    is_premium: Boolean(el.adminArticlePremium?.checked),
    is_published: Boolean(el.adminArticlePublished?.checked),
  };
  const path = selected ? `/api/admin/articles/${selected.id}` : "/api/admin/articles";
  const data = await api(path, { method: selected ? "PUT" : "POST", body });
  const saved = data.article;
  const index = (state.admin.articles || []).findIndex((item) => Number(item.id) === Number(saved.id));
  if (index >= 0) state.admin.articles[index] = saved;
  else state.admin.articles.unshift(saved);
  populateAdminArticleSelect(saved.id);
  await loadArticles({ selectId: saved.is_published ? saved.id : state.selectedArticleId });
  showToast(saved.is_published ? "Статья сохранена и опубликована." : "Черновик статьи сохранён.");
}

function applyEditedPhotoToAdminArticle(uploaded, editedFile, targetPreset, { inlineImage = null } = {}) {
  const photoPath = String(uploaded?.photo_path || "").trim();
  const photoUrl = String(uploaded?.photo_url || "").trim();
  if (!photoUrl) throw new Error("Сервер не вернул адрес изображения.");

  if (targetPreset === "avatar") {
    if (el.adminArticleCardImagePath) el.adminArticleCardImagePath.value = photoPath;
    if (el.adminArticleCardImagePreview) {
      el.adminArticleCardImagePreview.src = photoUrl;
      el.adminArticleCardImagePreview.classList.remove("hidden");
    }
    if (el.adminArticleCardImageFile) el.adminArticleCardImageFile.value = "";
    setResult(el.adminArticleResult, "Аватарка карточки настроена и загружена. Нажмите «Сохранить».");
    return;
  }

  if (targetPreset === "inline") {
    if (inlineImage) {
      inlineImage.src = photoUrl;
    } else {
      restoreArticleEditorSelection();
      document.execCommand(
        "insertHTML",
        false,
        `<figure><img src="${escapeHtml(photoUrl)}" alt="${escapeHtml(editedFile?.name || "Фотография статьи")}" loading="lazy"><figcaption></figcaption></figure><p><br></p>`,
      );
      rememberArticleEditorSelection();
    }
    if (el.adminArticleInlineImageFile) el.adminArticleInlineImageFile.value = "";
    setResult(el.adminArticleResult, "Фотография настроена и вставлена в текст. Нажмите «Сохранить».");
    return;
  }

  if (el.adminArticleCoverPath) el.adminArticleCoverPath.value = photoPath;
  if (el.adminArticleCoverPreview) {
    el.adminArticleCoverPreview.src = photoUrl;
    el.adminArticleCoverPreview.classList.remove("hidden");
  }
  if (el.adminArticleCoverFile) el.adminArticleCoverFile.value = "";
  setResult(el.adminArticleResult, "Обложка настроена и загружена. Нажмите «Сохранить».");
}

function openAdminArticleImageEditor({ file = null, url = "", targetPreset = "cover", inlineImage = null } = {}) {
  if (!file && !url) {
    const emptyLabel = targetPreset === "avatar" ? "аватарки" : targetPreset === "inline" ? "фотографии" : "обложки";
    showToast(`Выберите файл ${emptyLabel}.`, true);
    return;
  }
  void openArticlePhotoEditor({
    file,
    url: file ? "" : url,
    targetContext: targetPreset,
    onSave: (uploaded, editedFile, savedPreset) => {
      applyEditedPhotoToAdminArticle(uploaded, editedFile, savedPreset, { inlineImage });
    },
  });
}

function setupArticleEditorToolbar() {
  if (!el.adminArticleToolbar || !el.adminArticleEditor) return;
  el.adminArticleToolbar.querySelectorAll("[data-article-command]").forEach((button) => {
    button.addEventListener("mousedown", (event) => event.preventDefault());
    button.addEventListener("click", () => runArticleEditorCommand(button.dataset.articleCommand));
  });
  el.adminArticleBlockFormat?.addEventListener("change", () => {
    runArticleEditorCommand("formatBlock", el.adminArticleBlockFormat.value || "p");
  });
  ["keyup", "mouseup", "input", "focus"].forEach((eventName) => {
    el.adminArticleEditor.addEventListener(eventName, rememberArticleEditorSelection);
  });
  el.adminArticleEmojiBtn?.addEventListener("mousedown", (event) => event.preventDefault());
  el.adminArticleEmojiBtn?.addEventListener("click", () => {
    setArticleEmojiPickerOpen(el.adminArticleEmojiPanel?.hidden !== false);
  });
  el.adminArticleEmojiPanel?.querySelectorAll("[data-article-emoji]").forEach((button) => {
    button.addEventListener("mousedown", (event) => event.preventDefault());
    button.addEventListener("click", () => insertArticleEditorEmoji(button.dataset.articleEmoji));
  });
  document.addEventListener("click", (event) => {
    if (!el.adminArticleEmojiPanel || el.adminArticleEmojiPanel.hidden) return;
    if (!el.adminArticleToolbar?.contains(event.target)) setArticleEmojiPickerOpen(false);
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && el.adminArticleEmojiPanel?.hidden === false) {
      setArticleEmojiPickerOpen(false);
      el.adminArticleEmojiBtn?.focus();
    }
  });
}

async function refreshAdminBootstrap() {
  const [data] = await Promise.all([
    api("/api/admin/bootstrap"),
    loadAdminDashboard({ silent: true }),
  ]);
  state.admin.loaded = true;
  state.admin.categories = Array.isArray(data.categories) ? data.categories : [];
  state.admin.handbookSections = Array.isArray(data.handbook_sections) ? data.handbook_sections : [];
  state.admin.articles = Array.isArray(data.articles) ? data.articles : [];
  state.admin.subscriptionPlans = Array.isArray(data.subscription_plans) ? data.subscription_plans : [];
  state.admin.promocodes = Array.isArray(data.promocodes) ? data.promocodes : [];
  state.admin.faq = Array.isArray(data.faq) ? data.faq : [];
  state.admin.equipment = Array.isArray(data.equipment) ? data.equipment : [];
  state.admin.tickets = Array.isArray(data.tickets_open) ? data.tickets_open : [];
  state.admin.users = Array.isArray(data.users) ? data.users : [];
  state.admin.notifications = Array.isArray(data.notifications) ? data.notifications : [];
  state.admin.siteSocialLinks = normalizeSiteSocialLinks(data.site_social_links);
  state.admin.maintenance = normalizeMaintenanceStatus(data.maintenance || {});
  state.admin.registrationBonusSettings = data.registration_bonus_settings || {};
  state.admin.reviewBonusSettings = data.review_bonus_settings || state.admin.reviewBonusSettings || {};
  state.admin.referralSettings = data.referral_settings || state.admin.referralSettings || {};

  try {
    await ensureAdminStoreProductsLoaded();
  } catch (_error) {
    // Товары для привязки рецептов можно догрузить позже при открытии редактора.
  }

  const recipeRepair = data.recipe_repair || {};
  const menuRepointed = Number(recipeRepair.menu_repointed || 0);
  const relinked = Number(recipeRepair.relinked || 0);
  const created = Number(recipeRepair.created || 0);
  if (menuRepointed > 0 || relinked > 0 || created > 0) {
    const parts = [];
    if (menuRepointed > 0) parts.push(`связано с рецептом ${menuRepointed}`);
    if (relinked > 0) parts.push(`перепривязано ${relinked}`);
    if (created > 0) parts.push(`создано пустых ${created}`);
    showToast(`Синхронизация рецептов с каталогом: ${parts.join(", ")}.`);
  }

  populateAdminCategorySelects();
  populateAdminHandbookSectionSelect();
  populateAdminArticleSelect();
  populateAdminFaqSelect();
  populateAdminEquipmentSelect();
  populateAdminTicketsSelect();
  populateAdminUsersSelect();
  populateAdminNotificationUsers();
  renderAdminPlanSettings();
  renderAdminSiteSocialLinks();
  renderAdminMaintenanceSettings();
  renderRegistrationBonusSettings();
  renderAdminPromocodes();
  renderAdminNotificationsHistory();
  try { await loadAdminOrders(state.admin.ordersStatus || "current"); } catch (_error) { if (el.adminOrdersList) el.adminOrdersList.textContent = "Не удалось загрузить заказы."; }
  try {
    await loadAdminAudit();
  } catch (_error) {
    if (el.adminAuditMeta) {
      el.adminAuditMeta.textContent = "Не удалось загрузить аудит-лог.";
    }
  }
  const savedDbFilters = restoreAdminDbFilters();
  if (savedDbFilters) {
    applyAdminDbFiltersToUi(savedDbFilters);
    state.admin.dbLog.offset = Math.max(0, Number(savedDbFilters.offset || 0));
    if (savedDbFilters.auto_refresh && document.getElementById("admin-panel-db")?.classList.contains("is-active")) {
      startAdminDbAutoRefresh();
    }
  }
}

function storeOrderStatusText(status) {
  return ({
    new: "Новый",
    payment_pending: "Ожидает оплаты",
    paid: "Оплачен",
    ready: "Готов",
    cancelled: "Отменён",
    refunded: "Отменён / возврат",
    payment_failed: "Ошибка оплаты",
    payment_expired: "Просрочен",
  })[String(status || "")] || String(status || "—");
}

function customerOrderStatus(status) {
  return ({
    ready: { text: "Готов", className: "is-ready", hint: "Заказ готов к выдаче или отправке" },
    cancelled: { text: "Отменён", className: "is-cancelled", hint: "Заказ отменён администратором" },
    refunded: { text: "Возврат", className: "is-cancelled", hint: "Заказ отменён администратором, оформлен возврат" },
  })[String(status || "")] || { text: "Ожидает сборки", className: "is-packing", hint: "Оплата получена, скоро начнём сборку" };
}

function orderIdFromUrl() {
  return String(new URLSearchParams(window.location.search || "").get("order") || "").trim();
}

function storeOrderTotalsHtml(order) {
  const premiumDiscount = Math.max(0, Number(order.subscription_discount_kopecks || 0));
  const sausagesSpent = Math.max(0, Number(order.sausages_spent_kopecks || 0));
  const productsTotal = Number(order.products_total_kopecks ?? (order.items || []).reduce(
    (sum, item) => sum + Number(item.amount_kopecks || 0), 0,
  ));
  const originalTotal = Number(order.products_original_total_kopecks || (productsTotal + premiumDiscount));
  const rows = [{ label: "Товары", amount: originalTotal }];
  if (premiumDiscount > 0) {
    rows.push({ label: `Скидка PREMIUM −${Number(order.subscription_discount_percent || 5)}%`, amount: -premiumDiscount });
  }
  if (sausagesSpent > 0) {
    rows.push({ label: "Списано колбасок", amount: -sausagesSpent });
  }
  rows.push({ label: `Доставка (${order.delivery_method_title || "Доставка"})`, amount: Number(order.delivery_price_kopecks || 0) });
  rows.push({ label: "Итого", amount: Number(order.total_kopecks || 0), total: true });
  return `<dl class="store-order-totals">${rows.map((row) => `
    <div${row.total ? ' class="store-order-totals__final"' : ""}><dt>${escapeHtml(row.label)}</dt><dd>${row.amount < 0 ? "−" : ""}${escapeHtml(moneyRub(Math.abs(row.amount)))}</dd></div>
  `).join("")}</dl>`;
}

function renderAccountOrders(orders) {
  const wrap = document.getElementById("accountOrders");
  if (!wrap) return;
  const rows = Array.isArray(orders) ? orders : [];
  if (!rows.length) {
    setHtml(wrap, '<div class="account-orders-empty"><strong>История заказов пока пуста</strong><span>После успешной оплаты заказ появится здесь.</span></div>');
    return;
  }
  setHtml(wrap, rows.map((order) => {
    const status = customerOrderStatus(order.status);
    const hasPremiumDiscount = Number(order.subscription_discount_kopecks || 0) > 0;
    const items = (order.items || []).map((item) => `
      <li><span>${escapeHtml(item.title || "Товар")} <small>× ${escapeHtml(String(item.quantity || 1))}</small></span><strong>${escapeHtml(hasPremiumDiscount ? (item.original_amount_text || moneyRub(item.original_amount_kopecks || item.amount_kopecks || 0)) : (item.amount_text || moneyRub(item.amount_kopecks || 0)))}</strong></li>
    `).join("");
    return `<article class="account-order" data-order-id="${escapeHtml(order.order_id || "")}">
      <header><div><span class="account-order__number">Заказ № ${escapeHtml(order.order_id || "")}</span><time>${escapeHtml(formatMskDateTimeLabel(order.created_at, ""))}</time></div><span class="account-order__status ${status.className}">${status.text}</span></header>
      <p class="account-order__hint">${status.hint}</p>
      <ul>${items}</ul>
      ${storeOrderTotalsHtml(order)}
    </article>`;
  }).join(""));
  const requestedOrderId = orderIdFromUrl();
  if (requestedOrderId) {
    const target = Array.from(wrap.querySelectorAll("[data-order-id]")).find(
      (node) => String(node.dataset.orderId || "") === requestedOrderId,
    );
    if (target) {
      target.classList.add("is-highlighted");
      window.requestAnimationFrame(() => target.scrollIntoView({ behavior: "smooth", block: "center" }));
      window.setTimeout(() => target.classList.remove("is-highlighted"), 2600);
    }
  }
}

async function loadAccountOrders() {
  const wrap = document.getElementById("accountOrders");
  if (!wrap || !state.userId) { renderAccountOrders([]); return; }
  setHtml(wrap, '<div class="muted">Загрузка заказов…</div>');
  try {
    const data = await api("/api/store/orders");
    renderAccountOrders(data.items || []);
  } catch (_error) {
    setHtml(wrap, '<div class="account-orders-empty"><strong>Не удалось загрузить заказы</strong><span>Попробуйте обновить немного позже.</span></div>');
  }
}

function renderAdminOrders() {
  if (!el.adminOrdersList) return;
  const status = state.admin.ordersStatus || "current";
  const statuses = status === "completed" ? ["ready"]
    : status === "failed" ? ["cancelled", "refunded", "payment_failed", "payment_expired"]
      : ["paid"];
  const clients = (state.admin.orderClients || []).map((client) => ({
    ...client,
    orders: (Array.isArray(client.orders) ? client.orders : []).filter((order) => statuses.includes(order.status)),
  })).filter((client) => client.orders.length > 0);
  setHtml(el.adminOrdersList, "");
  el.adminOrdersList.classList.toggle("muted", clients.length === 0);
  if (!clients.length) {
    el.adminOrdersList.textContent = "Заказы не найдены.";
    return;
  }
  clients.forEach((client) => {
    const section = document.createElement("details");
    section.className = "admin-client-orders";
    const orders = Array.isArray(client.orders) ? client.orders : [];
    const successfulOrders = orders.filter((order) => ["paid", "ready"].includes(order.status));
    const total = successfulOrders.reduce((sum, order) => sum + Number(order.total_kopecks || 0), 0);
    const summary = status === "failed" ? `${orders.length} заказ(ов)`
      : `${successfulOrders.length} заказ(ов) • ${moneyRub(total)}`;
    setHtml(section, `
      <summary class="admin-client-orders__head">
        <div><strong>${escapeHtml(client.customer_name || "Без имени")}</strong><div class="muted">user #${escapeHtml(String(client.user_id || 0))} • ${escapeHtml(client.phone || "")}</div></div>
        <div>${escapeHtml(summary)}</div>
      </summary>
    `);
    orders.forEach((order) => {
      const article = document.createElement("article");
      const isPremiumOrder = String(order.subscription_tier_at_checkout || "").toLowerCase() === "premium";
      article.className = `ticket-item admin-order-card${isPremiumOrder ? " is-premium" : ""}`;
      const hasPremiumDiscount = Number(order.subscription_discount_kopecks || 0) > 0;
      const items = (order.items || []).map((item) => {
        const deliveredLabel = item.is_digital && item.digital_delivered ? " (отправлено)" : "";
        const amountText = hasPremiumDiscount
          ? (item.original_amount_text || moneyRub(item.original_amount_kopecks || item.amount_kopecks || 0))
          : (item.amount_text || moneyRub(item.amount_kopecks || 0));
        return `<li>${escapeHtml(`${item.title || "Товар"}${deliveredLabel}`)} × ${escapeHtml(String(item.quantity || 1))} — ${escapeHtml(amountText)}</li>`;
      }).join("");
      const canAct = String(order.status || "") === "paid";
      setHtml(article, `
        <div class="ticket-item__meta">#${escapeHtml(order.order_id || "")} ${isPremiumOrder ? '<span class="admin-order-premium-badge">PREMIUM • ПРИОРИТЕТ</span>' : ""} • ${escapeHtml(formatMskDateTimeLabel(order.created_at, ""))} • T-Bank: ${escapeHtml(order.tbank_status || "—")}</div>
        <div class="ticket-item__status">${escapeHtml(storeOrderStatusText(order.status))}</div>
        <div class="ticket-item__question"><strong>Доставка:</strong> ${escapeHtml(order.delivery_method_title || "")} (${escapeHtml(order.delivery_price_text || "")})<br><strong>Адрес:</strong> ${escapeHtml(order.address || "")}<br><strong>ФИО:</strong> ${escapeHtml(order.customer_name || "")}<br><strong>Телефон:</strong> ${escapeHtml(order.phone || "")}</div>
        <ul class="admin-order-items">${items}</ul>
        ${storeOrderTotalsHtml(order)}
      `);
      const actions = document.createElement("div");
      actions.className = "inline-actions";
      if (canAct) {
        const done = document.createElement("button");
        done.type = "button"; done.textContent = "Готово";
        done.onclick = () => updateAdminOrder(order.order_id, "complete");
        const cancel = document.createElement("button");
        cancel.type = "button"; cancel.className = "danger"; cancel.textContent = "Отмена";
        cancel.onclick = () => updateAdminOrder(order.order_id, "cancel");
        actions.append(done, cancel);
      }
      const print = document.createElement("a");
      print.className = "chip"; print.textContent = "Печать";
      print.href = `/admin/store/orders/${encodeURIComponent(order.order_id || "")}/print`;
      print.target = "_blank";
      print.rel = "noopener";
      actions.appendChild(print);
      article.appendChild(actions);
      section.appendChild(article);
    });
    el.adminOrdersList.appendChild(section);
  });
}

function renderAdminCarts() {
  if (!el.adminCartsList) return;
  const carts = Array.isArray(state.admin.carts) ? state.admin.carts : [];
  const summary = state.admin.cartsSummary || {};
  if (el.adminCartsSummary) {
    setHtml(el.adminCartsSummary, `
      <strong>Корзин: ${escapeHtml(String(summary.carts_count || 0))}</strong>
      <span>Позиций: ${escapeHtml(String(summary.positions_count || 0))}</span>
      <span>Товаров: ${escapeHtml(String(summary.items_quantity || 0))}</span>
      <span>Потенциальная сумма: ${escapeHtml(moneyRub(summary.total_kopecks || 0))}</span>
      <span>Средний чек: ${escapeHtml(moneyRub(summary.average_kopecks || 0))}</span>
    `);
  }
  setHtml(el.adminCartsList, "");
  el.adminCartsList.classList.toggle("muted", carts.length === 0);
  if (!carts.length) {
    el.adminCartsList.textContent = "Собранных корзин пока нет.";
    return;
  }
  carts.forEach((cart) => {
    const section = document.createElement("details");
    section.className = "admin-client-orders";
    const email = String(cart.email || "").trim();
    setHtml(section, `
      <summary class="admin-client-orders__head">
        <div>
          <strong>${escapeHtml(cart.username || `Пользователь #${cart.user_id || 0}`)}</strong>
          <div class="muted">user #${escapeHtml(String(cart.user_id || 0))}${email ? ` • ${escapeHtml(email)}` : ""}</div>
        </div>
        <div>${escapeHtml(String(cart.items_quantity || 0))} шт. • ${escapeHtml(moneyRub(cart.total_kopecks || 0))}</div>
      </summary>
      <article class="ticket-item admin-order-card">
        <div class="ticket-item__meta">Обновлена: ${escapeHtml(formatMskDateTimeLabel(cart.updated_at, "без даты"))}</div>
        <div class="ticket-item__status">${escapeHtml(String(cart.positions_count || 0))} поз. • ${escapeHtml(String(cart.total_weight_grams || 0))} г</div>
        <ul class="admin-order-items">${(cart.items || []).map((item) => `
          <li>${escapeHtml(item.title || `Товар #${item.product_id || 0}`)} × ${escapeHtml(String(item.quantity || 1))} — ${escapeHtml(moneyRub(item.amount_kopecks || 0))}${item.is_active ? "" : ' <span class="muted">(товар скрыт)</span>'}</li>
        `).join("")}</ul>
      </article>
    `);
    el.adminCartsList.appendChild(section);
  });
}

async function loadAdminCarts() {
  if (!el.adminCartsList) return;
  el.adminCartsList.textContent = "Загрузка…";
  try {
    const data = await api("/api/admin/store/carts?limit=500");
    state.admin.carts = Array.isArray(data.items) ? data.items : [];
    state.admin.cartsSummary = data.summary || {};
    renderAdminCarts();
  } catch (error) {
    el.adminCartsList.textContent = errorText(error, "Не удалось загрузить корзины.");
  }
}

async function loadAdminReviews(){
  const target=document.getElementById('adminReviewsList'); if(!target) return;
  target.textContent='Загрузка…';
  const data=await api('/api/admin/store/reviews'); setHtml(target,'');
  if(!(data.items||[]).length){target.textContent='Отзывов пока нет.';return;}
  data.items.forEach(r=>{const row=document.createElement('article');row.className='store-review admin-review';row.innerHTML=`<div><b>${escapeHtml(r.product_title||`Товар #${r.product_id}`)}</b> · ${renderRatingStars(r.rating)} · ${escapeHtml(reviewAuthorName(r))}${Number(r.review_bonus_kopecks||0)>0?` · <span class="store-review__bonus">Начислено ${sausageCountText(r.review_bonus_kopecks)}</span>`:''}<p>${r.review_text?escapeHtml(r.review_text):'<span class="muted">Пустой отзыв (только оценка)</span>'}</p></div>`;const button=document.createElement('button');button.type='button';button.className='danger';button.textContent='Удалить';button.onclick=async()=>{await api(`/api/admin/store/reviews/${r.id}`,{method:'DELETE'});await loadAdminReviews();};row.appendChild(button);target.appendChild(row);});
}

function adminBookHasSource(config=state.admin.digitalBook||{}) {
  return Boolean(config.source_sha256 || config.legacy_source || config.mobile?.source_sha256);
}
function adminBookVariantControls(variant='standard') {
  const mobile=variant==='mobile';
  return {
    book:mobile?(state.admin.digitalBook?.mobile||{}):(state.admin.digitalBook||{}),
    status:mobile?el.adminMobileBookStatus:el.adminDigitalBookStatus,
    file:mobile?el.adminMobileBookFile:el.adminDigitalBookFile,
    upload:mobile?el.adminMobileBookUploadBtn:el.adminDigitalBookUploadBtn,
    preview:mobile?el.adminMobileBookPreviewBtn:el.adminDigitalBookPreviewBtn,
  };
}
function collectAdminBookConfig() {
  return {title:String(document.getElementById('adminBookTitle')?.value||'').trim()};
}
function validateAdminBookTitle(config) {
  if(config.title) return true;
  showToast('Укажите название книги.',true);
  document.getElementById('adminBookTitle')?.focus();
  return false;
}
function fillAdminBookEditor(product={}) {
  state.admin.storeEditorGeneration=Number(state.admin.storeEditorGeneration||0)+1;
  const config=product.book_config||{};
  state.admin.digitalBook=config;
  const type=document.getElementById('adminStoreProductType');
  if(type) type.value=isStoreProductDigital(product)?'digital_book':'physical';
  const title=document.getElementById('adminBookTitle');
  if(title) title.value=config.title||'';
  if(el.adminDigitalBookFile) el.adminDigitalBookFile.value='';
  if(el.adminMobileBookFile) el.adminMobileBookFile.value='';
  updateAdminBookEditor();
}
function updateAdminBookEditor() {
  const digital=document.getElementById('adminStoreProductType')?.value==='digital_book';
  const manager=el.adminDigitalBookManager;
  if(manager){ manager.hidden=!digital; manager.classList.toggle('hidden',!digital); }
  document.querySelectorAll('[data-store-physical-field]').forEach(field=>{
    field.hidden=digital;
    field.classList.toggle('hidden',digital);
  });
  const title=document.getElementById('adminBookTitle');
  if(title) title.required=digital;
  renderAdminDigitalBookStatus();
}
function digitalBookSizeText(rawBytes) {
  const bytes = Math.max(0, Number(rawBytes) || 0);
  if (bytes < 1024) return `${Math.round(bytes)} Б`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} КБ`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} МБ`;
}
function renderAdminDigitalBookStatus() {
  const book=state.admin.digitalBook||{};
  const digital=document.getElementById('adminStoreProductType')?.value==='digital_book';
  const productId=document.getElementById('adminStoreProductId')?.value;
  const ready=adminBookHasSource(book);
  const busy=Boolean(state.admin.digitalBookUploading||state.admin.digitalBookPreviewing);
  const active=document.getElementById('adminStoreActive');
  if(active){
    active.disabled=digital&&!ready;
    if(digital&&!ready) active.checked=false;
  }
  for(const variant of ['standard','mobile']){
    const controls=adminBookVariantControls(variant);
    const source=controls.book;
    const variantReady=Boolean(source.source_sha256||(variant==='standard'&&source.legacy_source));
    if(controls.upload) controls.upload.disabled=!productId||busy;
    if(controls.preview) controls.preview.disabled=!variantReady||!productId||busy;
    if(controls.file) controls.file.disabled=busy;
    const details=[
      source.original_filename||'',
      Number(source.page_count||0)>0?`${Number(source.page_count)} стр.`:'',
      formatMskDateTimeLabel(source.uploaded_at,''),
      source.source_sha256?`версия ${String(source.source_sha256).slice(0,8)}`:'',
    ].filter(Boolean);
    if(controls.status) controls.status.textContent=!productId
      ? 'Черновик: сохраните карточку, затем загрузите DOCX или PDF. Покупатели пока не увидят книгу.'
      : !variantReady
        ? (ready?'Файл этой версии ещё не загружен.': 'Черновик: файл ещё не загружен. Книга скрыта от покупателей.')
        : `${active?.checked?'Публикация включена':'Черновик — скрыт от покупателей'}. ${details.join(' • ')||'Исходник первой книги подключён.'} После изменений сохраните карточку.`;
  }
}
async function uploadAdminDigitalBook(variant='standard') {
  if(state.admin.digitalBookUploading||state.admin.digitalBookPreviewing) return;
  const controls=adminBookVariantControls(variant);
  const productId=String(document.getElementById('adminStoreProductId')?.value||'');
  const editorGeneration=Number(state.admin.storeEditorGeneration||0);
  if(!productId){ showToast('Сначала сохраните карточку книги как черновик.',true); return; }
  const file=controls.file?.files?.[0];
  if(!file){ showToast('Выберите файл книги в DOCX или PDF.',true); return; }
  if(!/\.(docx|pdf)$/i.test(String(file.name||''))){
    showToast('Допустимы только документы .docx или .pdf.',true); return;
  }
  const body=new FormData();
  body.append('file',file);
  state.admin.digitalBookUploading=true;
  setButtonBusy(controls.upload,true,'Проверяем книгу…');
  renderAdminDigitalBookStatus();
  if(controls.status) controls.status.textContent=/\.pdf$/i.test(file.name)
    ? 'Загружаем PDF и проверяем файл…'
    : 'Загружаем DOCX и проверяем конвертацию в PDF…';
  try{
    const data=await api(`/api/admin/store/products/${encodeURIComponent(productId)}/book${variant==='mobile'?'?variant=mobile':''}`,{
      method:'POST',body,isFormData:true,timeoutMs:210000,
    });
    if(editorGeneration===Number(state.admin.storeEditorGeneration||0)
      && String(document.getElementById('adminStoreProductId')?.value||'')===productId){
      state.admin.digitalBook=data.product?.book_config||data.book||{};
      if(controls.file) controls.file.value='';
    }
    await refreshAdminStore();
    showToast(`${variant==='mobile'?'Мобильная версия':'Обычная версия'} проверена. Скачайте тестовый PDF перед публикацией.`);
  }catch(error){
    showToast(errorText(error,'Не удалось загрузить книгу. Предыдущий файл сохранён.'),true);
  }finally{
    state.admin.digitalBookUploading=false;
    setButtonBusy(controls.upload,false);
    renderAdminDigitalBookStatus();
  }
}
function attachmentFilename(response, fallback) {
  const disposition=String(response?.headers?.get('Content-Disposition')||'');
  const utf8=disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if(utf8){ try{return decodeURIComponent(utf8[1]);}catch(_error){} }
  const match=disposition.match(/filename="?([^";]+)"?/i);
  return match?.[1]||fallback;
}
async function downloadAdminDigitalBookPreview(variant='standard') {
  if(state.admin.digitalBookUploading||state.admin.digitalBookPreviewing) return;
  const controls=adminBookVariantControls(variant);
  const productId=String(document.getElementById('adminStoreProductId')?.value||'');
  if(!productId||!(controls.book.source_sha256||(variant==='standard'&&controls.book.legacy_source))){
    showToast('Сначала сохраните карточку и загрузите DOCX или PDF книги.',true); return;
  }
  const bookConfig=collectAdminBookConfig();
  if(!validateAdminBookTitle(bookConfig)) return;
  state.admin.digitalBookPreviewing=true;
  setButtonBusy(controls.preview,true,'Формируем PDF…');
  renderAdminDigitalBookStatus();
  try{
    const response=await api(`/api/admin/store/products/${encodeURIComponent(productId)}/book/preview`,{
      method:'POST',
      body:{book_config:bookConfig,source_sha256:controls.book.source_sha256||'',variant},
      timeoutMs:210000,
    });
    const blob=await response.blob();
    if(!blob.size) throw new Error('Сформирован пустой PDF.');
    const url=URL.createObjectURL(blob);
    const link=document.createElement('a');
    link.href=url;
    link.download=attachmentFilename(response,variant==='mobile'?'book_TEST_mobile.pdf':'book_TEST.pdf');
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(()=>URL.revokeObjectURL(url),60_000);
    showToast('Тестовый PDF скачан. После изменения названия сохраните карточку.');
  }catch(error){
    showToast(errorText(error,'Не удалось сформировать тестовый PDF.'),true);
  }finally{
    state.admin.digitalBookPreviewing=false;
    setButtonBusy(controls.preview,false);
    renderAdminDigitalBookStatus();
  }
}

async function loadAdminOrders(status = state.admin.ordersStatus || "current") {
  state.admin.ordersStatus = status;
  el.adminOrdersCurrentBtn?.classList.toggle("is-active", status === "current");
  el.adminOrdersCompletedBtn?.classList.toggle("is-active", status === "completed");
  el.adminOrdersFailedBtn?.classList.toggle("is-active", status === "failed");
  el.adminOrdersDigitalBtn?.classList.toggle("is-active", status === "digital");
  document.getElementById("adminDigitalBooksHelp")?.classList.toggle("hidden", status !== "digital");
  el.adminDigitalOrderTabs?.classList.toggle("hidden", status !== "digital");
  el.adminDigitalSummary?.classList.toggle("hidden", status !== "digital");
  if (status === "digital") {
    await loadAdminDigitalSales(state.admin.digitalSalesStatus || "delivered");
    return;
  }
  const data = await api(`/api/admin/store/orders?status=${encodeURIComponent(status)}&limit=500`);
  if (state.admin.ordersStatus !== status) return;
  state.admin.orderClients = Array.isArray(data.clients) ? data.clients : [];
  renderAdminOrders();
}

function digitalSaleStatusText(status) {
  return ({ delivered: "Отправлен", failed: "Ошибка", processing: "Отправляется", delivery_committed: "Отправка зафиксирована", reserved: "Ожидает отправки" })[String(status || "")] || String(status || "—");
}

function renderAdminDigitalSales() {
  if (!el.adminOrdersList) return;
  const items = state.admin.digitalSales || [];
  const summary = state.admin.digitalSalesSummary || [];
  setHtml(el.adminDigitalSummary, `<strong>Всего продано: ${escapeHtml(String(state.admin.digitalSalesTotal || 0))}</strong>${summary.map((item) => `<span>${escapeHtml(item.title || "Цифровой товар")} — ${escapeHtml(String(item.quantity || 0))} шт.</span>`).join("")}`);
  setHtml(el.adminOrdersList, "");
  el.adminOrdersList.classList.toggle("muted", items.length === 0);
  if (!items.length) {
    el.adminOrdersList.textContent = state.admin.digitalSalesStatus === "delivered" ? "Успешных отправок пока нет." : "Все цифровые товары успешно отправлены.";
    return;
  }
  items.forEach((item) => {
    const article = document.createElement("article");
    article.className = "ticket-item admin-digital-sale-card";
    setHtml(article, `
      <div class="ticket-item__meta">Заказ #${escapeHtml(item.order_id || "")} • экземпляр ${escapeHtml(item.instance_id || "—")}</div>
      <div class="ticket-item__status">${escapeHtml(digitalSaleStatusText(item.status))}</div>
      <div class="ticket-item__question"><strong>${escapeHtml(item.product_title || "Цифровой товар")}</strong><br>${escapeHtml(item.customer_name || "Без имени")} • ${escapeHtml(item.delivery_email || "Email не указан")}</div>
      <div class="muted">${escapeHtml(formatMskDateTimeLabel(item.delivered_at || item.updated_at || item.created_at, ""))}</div>
      ${item.error_text ? `<div class="error-text">${escapeHtml(item.error_text)}</div>` : ""}
    `);
    if (state.admin.digitalSalesStatus === "attention") {
      const retry = document.createElement("button");
      retry.type = "button";
      retry.textContent = "Повторить";
      retry.onclick = () => retryAdminDigitalSale(item.id, retry);
      article.appendChild(retry);
    }
    el.adminOrdersList.appendChild(article);
  });
}

async function loadAdminDigitalSales(status = state.admin.digitalSalesStatus || "delivered") {
  state.admin.digitalSalesStatus = status;
  el.adminDigitalDeliveredBtn?.classList.toggle("is-active", status === "delivered");
  el.adminDigitalAttentionBtn?.classList.toggle("is-active", status === "attention");
  const data = await api(`/api/admin/store/digital-sales?status=${encodeURIComponent(status)}&limit=500`);
  state.admin.digitalSales = Array.isArray(data.items) ? data.items : [];
  state.admin.digitalSalesSummary = Array.isArray(data.summary) ? data.summary : [];
  state.admin.digitalSalesTotal = Number(data.total_quantity || 0);
  renderAdminDigitalSales();
}

async function retryAdminDigitalSale(copyId, button) {
  if (!copyId || button?.disabled) return;
  if (button) { button.disabled = true; button.textContent = "Отправляем…"; }
  setResult(el.adminOrdersResult, "Повторяем отправку цифрового товара…");
  try {
    const data = await api(`/api/admin/store/digital-sales/${encodeURIComponent(copyId)}/retry`, { method: "POST", body: {} });
    setResult(el.adminOrdersResult, data.delivered || data.already_delivered ? "Товар успешно отправлен и перенесён в «Успешно»." : "Отправить товар не удалось. Проверьте ошибку и повторите попытку.", !(data.delivered || data.already_delivered));
    await loadAdminDigitalSales("attention");
  } catch (error) {
    setResult(el.adminOrdersResult, errorText(error, "Не удалось повторить отправку."), true);
    if (button) { button.disabled = false; button.textContent = "Повторить"; }
  }
}

async function updateAdminOrder(orderId, action) {
  if (!orderId) return;
  setResult(el.adminOrdersResult, action === "cancel" ? "Отменяем заказ и запрашиваем возврат…" : "Переносим заказ в готовые…");
  await api(`/api/admin/store/orders/${encodeURIComponent(orderId)}/${action}`, { method: "POST", body: {} });
  state.admin.dashboard.loadedAt = 0;
  setResult(el.adminOrdersResult, action === "cancel" ? "Заказ отменён и перенесён в отменённые и неоплаченные." : "Заказ готов и перенесён в завершённые.");
  await loadAdminOrders(state.admin.ordersStatus);
}

async function refreshAdminTickets() {
  const data = await api("/api/admin/support/tickets?status=all&limit=300");
  state.admin.tickets = Array.isArray(data.items) ? data.items : [];
  populateAdminTicketsSelect();
}

function buildAdminAuditQuery() {
  const params = new URLSearchParams();
  params.set("limit", String(state.admin.audit.limit || 100));
  params.set("offset", String(state.admin.audit.offset || 0));
  const method = String(el.adminAuditMethod?.value || "").trim().toUpperCase();
  const path = String(el.adminAuditPath?.value || "").trim();
  const ip = String(el.adminAuditIp?.value || "").trim();
  const requestId = String(el.adminAuditRequestId?.value || "").trim();
  const statusFrom = String(el.adminAuditStatusFrom?.value || "").trim();
  const statusTo = String(el.adminAuditStatusTo?.value || "").trim();
  const adminUserId = String(el.adminAuditAdminUserId?.value || "").trim();
  const createdFrom = String(el.adminAuditCreatedFrom?.value || "").trim();
  const createdTo = String(el.adminAuditCreatedTo?.value || "").trim();
  if (method) params.set("method", method);
  if (path) params.set("path", path);
  if (ip) params.set("ip", ip);
  if (requestId) params.set("request_id", requestId);
  if (statusFrom) params.set("status_from", statusFrom);
  if (statusTo) params.set("status_to", statusTo);
  if (adminUserId) params.set("admin_user_id", adminUserId);
  if (createdFrom) params.set("created_from", createdFrom);
  if (createdTo) params.set("created_to", createdTo);
  return params.toString();
}

function renderAdminAuditList() {
  if (!el.adminAuditList) return;
  const items = state.admin.audit.items || [];
  setHtml(el.adminAuditList, "");
  if (!items.length) {
    el.adminAuditList.classList.add("muted");
    el.adminAuditList.textContent = "Записи не найдены.";
  } else {
    el.adminAuditList.classList.remove("muted");
    items.forEach((item) => {
      const row = document.createElement("article");
      row.className = "ticket-item";
      setHtml(row, `
        <div class="ticket-item__meta">#${escapeHtml(String(item.id || ""))} • admin #${escapeHtml(String(item.admin_user_id || ""))} • ${escapeHtml(formatMskDateTimeLabel(item.created_at, ""))}</div>
        <div class="ticket-item__status">${escapeHtml(String(item.method || ""))} ${escapeHtml(String(item.path || ""))} • ${escapeHtml(String(item.status_code || ""))}</div>
        <div class="ticket-item__question">IP: ${escapeHtml(String(item.ip_address || ""))} • request_id: ${escapeHtml(String(item.request_id || ""))}</div>
        <div class="ticket-item__answer">${escapeHtml(String(item.details || ""))}</div>
      `);
      el.adminAuditList.appendChild(row);
    });
  }
  if (el.adminAuditMeta) {
    const offset = Number(state.admin.audit.offset || 0);
    const limit = Number(state.admin.audit.limit || 100);
    const total = Number(state.admin.audit.total || 0);
    const from = total > 0 ? offset + 1 : 0;
    const to = Math.min(offset + limit, total);
    el.adminAuditMeta.textContent = `Показано ${from}-${to} из ${total}`;
  }
}

async function loadAdminAudit() {
  const query = buildAdminAuditQuery();
  const data = await api(`/api/admin/security/audit?${query}`);
  state.admin.audit.items = Array.isArray(data.items) ? data.items : [];
  state.admin.audit.total = Number(data.total || 0);
  state.admin.audit.limit = Number(data.limit || state.admin.audit.limit || 100);
  state.admin.audit.offset = Number(data.offset || 0);
  renderAdminAuditList();
}

function bindMainEvents() {
  document.getElementById("checkoutForm")?.addEventListener("submit", submitCheckout);
  document.getElementById("cartSausageToggleBtn")?.addEventListener("click", () => {
    state.store.useSausages=!state.store.useSausages;
    reconcileSausageRedemption();
    renderCart();
  });
  el.storePromoPrev?.addEventListener("click",()=>moveStorePromoCarousel(-1));
  el.storePromoNext?.addEventListener("click",()=>moveStorePromoCarousel(1));
  el.storePromoBanner?.addEventListener('click',(event)=>{
      const dot=event.target.closest('[data-store-promo-dot]');
      if(!dot) return;
      const code=String(dot.dataset.storePromoDot||'');
      const index=visibleStorePromoSlides().findIndex((slide)=>String(slide.dataset.storePromoSlide||'')===code);
      if(index>=0){ showStorePromoSlide(index); startStorePromoCarousel(); }
  });
  document.getElementById('adminStoreBannersSave')?.addEventListener('click',saveAdminBanners);
  document.getElementById('adminStoreBannersReload')?.addEventListener('click',()=>{
    if(state.admin.storeBannersSaving) return;
    if(state.admin.storeBannersDirty&&!window.confirm('Отменить несохранённые правки и загрузить актуальные баннеры?')) return;
    void loadAdminBanners({discard:true});
  });
  document.getElementById('adminStoreBannerAdd')?.addEventListener('click',()=>{
    const collection=state.admin.storeBanners;
    if(!collection||collection.banners.length>=20||state.admin.storeBannersSaving) return;
    collection.banners.push({id:crypto.randomUUID(),kind:'custom',title:'Новый баннер',text:'',image_url:'',link_url:'',link_text:'',is_active:true,sort_order:collection.banners.length});
    markAdminBannersDirty(); renderAdminBanners();
  });
  document.getElementById("storeSearchInput")?.addEventListener("input", (event) => {
    scheduleStoreSearch(event.currentTarget?.value || "");
  });
  const storeSearchInput = document.getElementById("storeSearchInput");
  if (storeSearchInput) {
    storeSearchInput.value = "";
    // Chrome часто показывает менеджер паролей; readonly до фокуса + one-time-code это глушит.
    const unlockSearch = () => {
      storeSearchInput.removeAttribute("readonly");
      storeSearchInput.setAttribute("autocomplete", "one-time-code");
    };
    storeSearchInput.addEventListener("focus", unlockSearch);
    storeSearchInput.addEventListener("pointerdown", unlockSearch);
    storeSearchInput.addEventListener("keydown", (event) => {
      if (event.key === "Enter") event.preventDefault();
    });
  }
  document.getElementById("deliveryMethodSelect")?.addEventListener("change", async () => {
    renderCart();
    if(ozonDeliveryRequired() && state.store.ozonDelivery?.selectedPoint && !currentOzonDeliveryEstimate()){
      await refreshOzonDeliveryEstimate({quiet:true});
    }
  });
  document.getElementById("ozonPvzOpenBtn")?.addEventListener("click", openOzonPvzDialog);
  document.getElementById("ozonPvzCloseBtn")?.addEventListener("click", closeOzonPvzDialog);
  document.getElementById("ozonPvzSearchForm")?.addEventListener("submit", searchOzonPvz);
  document.getElementById("ozonPvzSearchInput")?.addEventListener("input", scheduleOzonPvzSearch);
  document.querySelector('#checkoutForm [name="phone"]')?.addEventListener("input", () => {
    if(state.store.ozonDelivery?.quote){
      invalidateOzonDeliveryQuote();
      renderCart();
    }
  });
  document.getElementById("cartSummary")?.addEventListener("click", () => {
    activateTab("home");
    document.getElementById("cartCard")?.scrollIntoView({ behavior: "smooth", block: "start" });
  });
  if (el.notificationsBtn) {
    el.notificationsBtn.addEventListener("click", async (event) => {
      event.stopPropagation();
      await toggleNotificationsPopover();
    });
  }
  if (el.notificationsClearBtn) {
    el.notificationsClearBtn.addEventListener("click", (event) => {
      event.stopPropagation();
      void clearReadNotifications();
    });
  }
  if (el.notificationsReadAllBtn) {
    el.notificationsReadAllBtn.addEventListener("click", (event) => {
      event.stopPropagation();
      void markAllNotificationsRead();
    });
  }
  if (el.notificationPermissionEnableBtn) {
    el.notificationPermissionEnableBtn.addEventListener("click", () => {
      void requestDesktopNotificationPermission();
    });
  }
  if (el.notificationPermissionDismissBtn) {
    el.notificationPermissionDismissBtn.addEventListener("click", () => {
      setNotificationPermissionPromptDismissed(true);
      syncNotificationPermissionPrompt();
    });
  }
  document.addEventListener("click", (event) => {
    if (!el.notificationsPopover || el.notificationsPopover.classList.contains("hidden")) return;
    const target = event.target;
    const path = typeof event.composedPath === "function" ? event.composedPath() : [];
    if (
      path.includes(el.notificationsPopover)
      || path.includes(el.notificationsBtn)
      || el.notificationsPopover.contains(target)
      || el.notificationsBtn?.contains(target)
    ) return;
    closeNotificationsPopover();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") closeNotificationsPopover();
  });
  window.addEventListener("resize", positionNotificationsPopover);
  window.addEventListener("scroll", positionNotificationsPopover, true);
  if (el.authBtn) {
    el.authBtn.addEventListener("click", () => {
      redirectToAuth("login");
    });
  }
  if (el.guestLoginBtn) {
    el.guestLoginBtn.addEventListener("click", () => {
      redirectToAuth("login");
    });
  }
  if (el.guestRegisterBtn) {
    el.guestRegisterBtn.addEventListener("click", () => {
      redirectToAuth("register");
    });
  }

  if (el.disableAutopayBtn) {
    el.disableAutopayBtn.addEventListener("click", async () => {
      if (!state.isAuthenticated) {
        openPaywall("auth_required");
        return;
      }
      if (!window.confirm("Отключить автоплатёж и отвязать сохраненные реквизиты?")) return;
      setButtonBusy(el.disableAutopayBtn, true, "Отключаем...");
      try {
        await api("/api/account/autopay/disable", { method: "POST", body: { user_id: state.userId } });
        setResult(el.billingResult, "Автоплатёж отключён.");
        await loadBillingInfo();
        showToast("Автоплатёж отключён.");
      } catch (error) {
        setResult(el.billingResult, error.message, true);
      } finally {
        setButtonBusy(el.disableAutopayBtn, false);
      }
    });
  }

  el.accountEmailLinkForm?.addEventListener("submit", (event) => {
    event.preventDefault();
    if (state.accountEmail.verificationStarted) void confirmAccountEmailLink();
    else void startAccountEmailLink();
  });
  el.accountEmailSendBtn?.addEventListener("click", () => void startAccountEmailLink());
  el.accountEmailConfirmBtn?.addEventListener("click", () => void confirmAccountEmailLink());
  el.accountEmailResendBtn?.addEventListener("click", () => void startAccountEmailLink({ resend: true }));
  el.accountEmailChangeBtn?.addEventListener("click", () => {
    resetAccountEmailLinkUi();
    setResult(el.accountEmailResult, "");
    el.accountEmailInput?.focus();
  });

  el.accountOauthConsent?.addEventListener("change", updateAccountOauthActionsState);
  el.accountLinkGoogleBtn?.addEventListener("click", startAccountGoogleLink);
  el.accountOauthRefresh?.addEventListener("click", async () => {
    if (el.accountOauthRefresh.disabled) return;
    setButtonBusy(el.accountOauthRefresh, true);
    try {
      await loadAccountOauthConnections({ initializeProviders: true, forceProviderRefresh: true });
    } finally {
      setButtonBusy(el.accountOauthRefresh, false);
    }
  });
  el.accountOauthCard?.addEventListener("toggle", () => {
    if (!el.accountOauthCard.open || !state.isAuthenticated) return;
    if (state.accountOauth.loaded) {
      void initAccountOauthProviders();
      return;
    }
    void loadAccountOauthConnections({ initializeProviders: true });
  });

  el.categorySelect.addEventListener("change", async () => {
    state.selectedCategory = el.categorySelect.value;
    state.recipeSearchQuery = "";
    if (el.recipeSearchInput) el.recipeSearchInput.value = "";
    try {
      await loadSausagesForCategory(state.selectedCategory);
    } catch (error) {
      setResult(el.recipeText, error.message, true);
    }
  });

  el.recipeSearchInput?.addEventListener("input", async () => {
    state.recipeSearchQuery = el.recipeSearchInput.value;
    try {
      await renderRecipeSelectorForCurrentFilters();
    } catch (error) {
      setResult(el.recipeText, error.message || "Не удалось выполнить поиск рецептов.", true);
    }
  });

  el.sausageSelect.addEventListener("change", async () => {
    try {
      await onSausageChange(el.sausageSelect.value);
    } catch (error) {
      setResult(el.recipeText, error.message, true);
    }
  });

  if (el.recipeAddToCartBtn) {
    el.recipeAddToCartBtn.addEventListener("click", () => {
      try {
        addRecipeIngredientsToCart();
      } catch (error) {
        showToast(error.message, true);
      }
    });
  }

  if (el.customAddIngredientRow) {
    el.customAddIngredientRow.addEventListener("click", () => {
      el.customIngredientsRows?.appendChild(makeCustomIngredientRow());
    });
  }

  el.recipeCalcForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const submitBtn = el.recipeCalcForm.querySelector("button[type='submit']");
    setButtonBusy(submitBtn, true, "Считаем...");
    try {
      await runRecipeCalculation();
    } catch (error) {
      setResult(el.recipeCalcResult, error.message, true);
      el.recipeCalcResult.classList.remove("muted");
    } finally {
      setButtonBusy(submitBtn, false);
    }
  });

  el.customCalcForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const submitBtn = el.customCalcForm.querySelector("button[type='submit']");
    setButtonBusy(submitBtn, true, "Считаем...");
    try {
      const fd = new FormData(el.customCalcForm);
      const linesText = collectCustomLinesFromRows();
      const linesField = el.customCalcForm.elements.namedItem("lines");
      if (linesField) {
        linesField.value = linesText;
      }
      const result = await api("/api/calc/custom", {
        method: "POST",
        body: {
          user_id: state.userId,
          weight_kg: Number(fd.get("weight") || 0),
          lines: linesText,
        },
      });
      state.lastCustomLines = linesText;
      state.lastCustomCalcWeight = Number(fd.get("weight") || 1);
      renderCustomResult(result, linesText);
    } catch (error) {
      setResult(el.customCalcResult, error.message, true);
      el.customCalcResult.classList.remove("muted");
    } finally {
      setButtonBusy(submitBtn, false);
    }
  });

  el.recipePrintPdfBtn?.addEventListener("click", async () => {
    setButtonBusy(el.recipePrintPdfBtn, true, "PDF...");
    try {
      await printRecipePdf();
      showToast("PDF открыт в новой вкладке.");
    } catch (error) {
      showToast(error.message || "Не удалось сформировать PDF.", true);
    } finally {
      setButtonBusy(el.recipePrintPdfBtn, false);
    }
  });

  el.customPrintPdfBtn?.addEventListener("click", async () => {
    setButtonBusy(el.customPrintPdfBtn, true, "PDF...");
    try {
      await printCustomPdf();
      showToast("PDF открыт в новой вкладке.");
    } catch (error) {
      showToast(error.message || "Не удалось сформировать PDF.", true);
    } finally {
      setButtonBusy(el.customPrintPdfBtn, false);
    }
  });

  el.handbookSectionSelect.addEventListener("change", () => {
    renderHandbookArticles(el.handbookSectionSelect.value);
  });

  el.handbookArticleSelect.addEventListener("change", () => {
    const section = state.handbookSections.find((s) => s.key === el.handbookSectionSelect.value);
    const article = section?.articles?.find((a) => a.key === el.handbookArticleSelect.value);
    if (article) {
      renderHandbookArticle(article);
    }
  });

  el.articleLikeBtn?.addEventListener("click", () => void reactToArticle(1));
  el.articleDislikeBtn?.addEventListener("click", () => void reactToArticle(-1));
  el.articleShareBtn?.addEventListener("click", () => {
    const article = state.articleDetail?.article;
    if (article) void copyArticleLink(article, el.articleShareBtn);
  });
  el.articleMobileBackBtn?.addEventListener("click", () => setArticleReadingMode(false));
  el.articleCommentForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const article = state.articleDetail?.article;
    if (!article) return;
    if (!state.isAuthenticated) {
      redirectToAuth("login");
      return;
    }
    const text = String(el.articleCommentText?.value || "").trim();
    if (text.length < 2) {
      showToast("Напишите комментарий не короче 2 символов.", true);
      return;
    }
    const submitButton = el.articleCommentForm.querySelector('button[type="submit"]');
    setButtonBusy(submitButton, true, "Отправляем…");
    try {
      await publishArticleComment(text, 0);
      if (el.articleCommentText) el.articleCommentText.value = "";
      showToast("Комментарий опубликован.");
    } catch (error) {
      showToast(errorText(error, "Не удалось отправить комментарий."), true);
    } finally {
      setButtonBusy(submitButton, false);
    }
  });

  el.supportForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const submitBtn = el.supportForm.querySelector("button[type='submit']");
    setButtonBusy(submitBtn, true, "Отправляем...");
    if (!state.isAuthenticated) {
      openPaywall("auth_required");
      setButtonBusy(submitBtn, false);
      return;
    }
    const fd = new FormData(el.supportForm);
    const text = String(fd.get("text") || "").trim();

    try {
      const result = await api("/api/support/tickets", {
        method: "POST",
        body: {
          user_id: state.userId,
          text,
        },
      });
      setResult(el.supportResult, `Обращение принято. Номер: ${result.ticket_id}`);
      showToast("Обращение отправлено.");
      el.supportForm.reset();
      await loadMyTickets();
    } catch (error) {
      setResult(el.supportResult, error.message, true);
    } finally {
      setButtonBusy(submitBtn, false);
    }
  });

  el.reloadMyTickets.addEventListener("click", async () => {
    if (!state.isAuthenticated) {
      openPaywall("auth_required");
      return;
    }
    try {
      await loadMyTickets();
    } catch (error) {
      setResult(el.myTickets, error.message, true);
    }
  });

  if (el.applyPaymentPromoCodeBtn && el.paymentPromoCode) {
    el.applyPaymentPromoCodeBtn.addEventListener("click", async () => {
      if (!state.isAuthenticated) {
        openPaywall("auth_required");
        return;
      }
      const promoCode = String(el.paymentPromoCode?.value || "").trim().toUpperCase();
      if (!promoCode) {
        state.promoPreview = null;
        renderPaymentPlans();
        return;
      }

      setButtonBusy(el.applyPaymentPromoCodeBtn, true, "Применяем...");
      try {
        const preview = await api("/api/payments/tbank/preview-promo", {
          method: "POST",
          body: { promo_code: promoCode },
        });

        const tier = String(preview?.tier || "").trim().toLowerCase();
        if (tier !== "premium") {
          throw new Error("Тариф для промокода не найден.");
        }

        const duration = Number(preview?.duration_days || state.payments?.duration_days || 30);
        const firstMonth = Math.round(
          Number(
            preview?.first_month_amount_rub ?? preview?.amount_rub ?? 0,
          ),
        );
        const regular = Math.round(Number(preview?.regular_amount_rub ?? firstMonth));
        const baseList = Math.round(Number(preview?.base_amount_rub || 0));

        state.promoPreview = {
          tier,
          code: promoCode,
          base_amount_rub: baseList,
          first_month_amount_rub: firstMonth,
          regular_amount_rub: regular,
          amount_rub: firstMonth,
          duration_days: duration,
        };
        renderPaymentPlans();

        if (firstMonth < regular) {
          showToast(
            `Промокод ${promoCode}: первый период ${firstMonth} ₽, далее автопродление ${regular} ₽ / ${duration} дн.`,
          );
        } else {
          showToast(`Промокод ${promoCode} применён к тарифу ${humanTierTitle(tier)}.`);
        }
      } catch (error) {
        state.promoPreview = null;
        renderPaymentPlans();
        showToast(errorText(error, "Не удалось применить промокод."), true);
      } finally {
        setButtonBusy(el.applyPaymentPromoCodeBtn, false);
      }
    });
  }

  el.premiumLink?.addEventListener("click", async (event) => {
    event.preventDefault();
    await startTbankPayment("premium", el.premiumLink);
  });

  if (el.logoutBtn) {
    el.logoutBtn.addEventListener("click", async () => {
      try {
        try {
          await unsubscribePushNotifications();
        } catch (_pushError) {
          // Выход из аккаунта не должен зависеть от доступности Push API.
        }
        await api("/api/auth/logout", { method: "POST", skipAuthRedirect: true });
      } catch (_error) {
        // Даже если logout API недоступен, уводим на страницу входа.
      } finally {
        redirectToAuth("login");
      }
    });
  }
}

async function loadBillingInfo() {
  if (!el.billingInfo) return;
  if (!state.isAuthenticated) {
    state.billing = null;
    setHtml(
      el.billingInfo,
      `<div class="account-subscription-hero">
        <span class="account-plan-badge">Демо</span>
        <div>
          <h3>Подписка доступна после входа</h3>
          <p>Войдите, чтобы оформить PREMIUM и управлять продлением.</p>
        </div>
      </div>`,
    );
    if (el.disableAutopayBtn) {
      el.disableAutopayBtn.classList.add("hidden");
    }
    el.accountSubscriptionCard?.classList.remove("is-active-plan");
    renderAccountSubscriptionPlans();
    return;
  }
  try {
    const billing = await api("/api/account/billing");
    state.billing = billing || null;
    let status = String(billing?.status || "inactive").trim().toLowerCase() || "inactive";
    const until = billing?.active_until ? String(billing.active_until) : "";
    const untilDt = parseStorageDateTime(until);
    if (status === "active" && untilDt && untilDt.getTime() <= Date.now()) {
      status = "expired";
    }
    const tier = String(billing?.tier || "none").trim().toLowerCase();
    const isActive = tier === "premium" && status === "active";
    const autopayEnabled = Boolean(billing?.autopay_enabled);
    const method = String(billing?.autopay_method || "").trim().toLowerCase();
    const cardMask = String(billing?.card_mask || "").trim();
    let paymentMethod = "Не сохранён";
    if (method === "sbp" && billing?.has_sbp) {
      paymentMethod = "СБП";
    } else if (cardMask) {
      paymentMethod = `Карта ${cardMask}`;
    } else if (billing?.has_rebill) {
      paymentMethod = "Банковская карта";
    }
    const activeUntilLabel = until ? formatMskDateTimeLabel(until, until) : "";
    const title = isActive ? "Подписка активна" : status === "expired" ? "Подписка закончилась" : "PREMIUM не оформлен";
    const description = isActive
      ? activeUntilLabel
        ? `Доступ открыт до ${activeUntilLabel}.`
        : "Все возможности PREMIUM доступны."
      : status === "expired"
        ? "Оформите подписку снова, чтобы вернуть PREMIUM-возможности."
        : "Разделы доступны уже сейчас. PREMIUM можно оформить ниже.";
    const renewal = isActive
      ? autopayEnabled ? "Продлится автоматически" : "Автопродление выключено"
      : "Не подключено";
    setHtml(
      el.billingInfo,
      `<div class="account-subscription-hero">
        <span class="account-plan-badge${isActive ? " is-premium" : ""}">${isActive ? "PREMIUM" : "Базовый"}</span>
        <div>
          <h3>${escapeHtml(title)}</h3>
          <p>${escapeHtml(description)}</p>
        </div>
      </div>
      <div class="account-subscription-meta">
        <div>
          <span>Продление</span>
          <strong>${escapeHtml(renewal)}</strong>
        </div>
        <div>
          <span>Способ оплаты</span>
          <strong>${escapeHtml(autopayEnabled ? paymentMethod : "Не используется")}</strong>
        </div>
      </div>`,
    );
    el.accountSubscriptionCard?.classList.toggle("is-active-plan", isActive);
    if (el.disableAutopayBtn) {
      el.disableAutopayBtn.disabled = false;
      el.disableAutopayBtn.classList.toggle("hidden", !autopayEnabled);
    }
    renderAccountSubscriptionPlans();
  } catch (error) {
    state.billing = null;
    setResult(el.billingInfo, error.message, true);
    el.accountSubscriptionCard?.classList.remove("is-active-plan");
    el.disableAutopayBtn?.classList.add("hidden");
    renderAccountSubscriptionPlans();
  }
}

function accountOauthProviderLabel(provider) {
  return ({ google: "Google", vkid: "VK ID", mail_ru: "Mail.ru", ok_ru: "Одноклассники" })[
    String(provider || "")
  ] || "Внешний аккаунт";
}

function accountOauthConnectionMap() {
  return new Map(
    (state.accountOauth.connections || []).map((item) => [String(item?.code || ""), item]),
  );
}

function renderAccountOauthConnections() {
  if (!el.accountOauthConnections) return;
  const connections = Array.isArray(state.accountOauth.connections) ? state.accountOauth.connections : [];
  const connectedCount = connections.filter((item) => Boolean(item?.connected)).length;
  if (el.accountOauthSummary) {
    el.accountOauthSummary.textContent = connectedCount > 0
      ? `${connectedCount} подключено`
      : "Можно подключить";
    el.accountOauthSummary.classList.toggle("is-connected", connectedCount > 0);
  }
  if (!connections.length) {
    setHtml(el.accountOauthConnections, '<div class="muted">Способы входа пока не загружены.</div>');
    return;
  }
  setHtml(
    el.accountOauthConnections,
    connections.map((item) => {
      const connected = Boolean(item?.connected);
      const detail = connected
        ? String(item?.email || "Привязан к этому кабинету")
        : item?.enabled === false
          ? "Сервис не настроен"
          : "Не привязан";
      return `<div class="account-oauth-connection${connected ? " is-connected" : ""}">
        <strong><span class="account-oauth-status-dot" aria-hidden="true"></span>${escapeHtml(item?.label || accountOauthProviderLabel(item?.code))}</strong>
        <small title="${escapeHtml(detail)}">${escapeHtml(detail)}</small>
      </div>`;
    }).join(""),
  );
  updateAccountOauthActionsState();
}

function updateAccountOauthActionsState() {
  const consent = Boolean(el.accountOauthConsent?.checked);
  const byProvider = accountOauthConnectionMap();
  const google = byProvider.get("google") || {};
  if (el.accountLinkGoogleBtn) {
    el.accountLinkGoogleBtn.classList.toggle("hidden", Boolean(google.connected) || google.enabled === false);
    el.accountLinkGoogleBtn.disabled = !consent;
  }
  const hosts = [
    ["vkid", el.accountVkProviderVk],
    ["mail_ru", el.accountVkProviderMail],
    ["ok_ru", el.accountVkProviderOk],
  ];
  for (const [code, host] of hosts) {
    if (!host) continue;
    const connection = byProvider.get(code) || {};
    host.classList.toggle("hidden", Boolean(connection.connected) || connection.enabled === false);
    host.classList.toggle("is-disabled", !consent);
    host.setAttribute("aria-disabled", consent ? "false" : "true");
  }
}

function generateAccountVkCodeVerifier() {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
  const bytes = new Uint8Array(64);
  crypto.getRandomValues(bytes);
  let result = "";
  for (let index = 0; index < bytes.length; index += 1) {
    result += alphabet[bytes[index] % alphabet.length];
  }
  return result;
}

function loadAccountVkSdk() {
  if ("VKIDSDK" in window) return Promise.resolve();
  const src = "https://cdn.jsdelivr.net/npm/@vkid/sdk@2.5.2/dist-sdk/umd/index.js";
  const integrity = "sha384-BKA+oneIRkxMwRXkP0ZaMmIm0aSpEBPqqWHl236XKJrA6brrb8y70DkCZd5QkivN";
  return new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[data-src="${src}"]`);
    if (existing) {
      if (existing.dataset.loaded === "1") resolve();
      else {
        existing.addEventListener("load", resolve, { once: true });
        existing.addEventListener("error", () => reject(new Error("VK ID SDK недоступен.")), { once: true });
      }
      return;
    }
    const script = document.createElement("script");
    script.src = src;
    script.async = true;
    script.integrity = integrity;
    script.crossOrigin = "anonymous";
    script.dataset.src = src;
    script.addEventListener("load", () => {
      script.dataset.loaded = "1";
      resolve();
    }, { once: true });
    script.addEventListener("error", () => reject(new Error("VK ID SDK недоступен.")), { once: true });
    document.head.appendChild(script);
  });
}

async function completeAccountVkLink(payload, provider) {
  if (!el.accountOauthConsent?.checked) {
    setResult(el.accountOauthResult, "Сначала подтвердите согласие на получение данных аккаунта.", true);
    return;
  }
  const data = await api("/api/account/oauth/vk/link", {
    method: "POST",
    body: {
      code: String(payload?.code || ""),
      device_id: String(payload?.device_id || ""),
      code_verifier: state.accountOauth.vkCodeVerifier,
      state: String(payload?.state || ""),
      provider,
      profile_consent: true,
    },
  });
  state.accountOauth.connections = Array.isArray(data?.connections) ? data.connections : [];
  renderAccountOauthConnections();
  const label = data?.provider_label || accountOauthProviderLabel(provider);
  setResult(el.accountOauthResult, `${label} успешно привязан.`);
  showToast(`${label} успешно привязан.`);
  await initAccountOauthProviders({ force: true });
}

function pendingAccountOauthProviderCodes() {
  const byProvider = accountOauthConnectionMap();
  return ["vkid", "mail_ru", "ok_ru"].filter((code) => {
    const item = byProvider.get(code) || {};
    return !item.connected && item.enabled !== false;
  });
}

async function initAccountOauthProviders({ force = false } = {}) {
  if (!state.isAuthenticated || !el.accountVkProviderVk) return;
  // Opening the card and refreshing it can reach this function concurrently.
  if (state.accountOauth.vkInitPromise) return state.accountOauth.vkInitPromise;
  const providerCodes = pendingAccountOauthProviderCodes().join(",");
  if (state.accountOauth.vkInitialized && state.accountOauth.vkProviderCodes === providerCodes && !force) {
    updateAccountOauthActionsState();
    return;
  }
  state.accountOauth.vkInitPromise = renderAccountOauthProviders();
  try {
    await state.accountOauth.vkInitPromise;
  } finally {
    state.accountOauth.vkInitPromise = null;
  }
}

function clearAccountOauthProviders() {
  const hosts = [el.accountVkProviderVk, el.accountVkProviderMail, el.accountVkProviderOk];
  (state.accountOauth.vkWidgets || []).forEach((widget) => {
    try { widget?.close?.(); } catch (_error) { /* Widget is already detached. */ }
  });
  state.accountOauth.vkWidgets = [];
  state.accountOauth.vkInitialized = false;
  state.accountOauth.vkProviderCodes = "";
  hosts.forEach((host) => { if (host) host.innerHTML = ""; });
}

async function renderAccountOauthProviders() {
  // SDK render appends to its host, so every rebuild must dispose the old set.
  clearAccountOauthProviders();
  try {
    if (!pendingAccountOauthProviderCodes().length) {
      state.accountOauth.vkInitialized = true;
      updateAccountOauthActionsState();
      return;
    }
    const config = await api("/api/account/oauth/vk/config");
    if (!config?.enabled) return;
    setResult(el.accountOauthResult, "");
    await loadAccountVkSdk();
    if (!("VKIDSDK" in window)) throw new Error("VK ID SDK недоступен.");
    const VKID = window.VKIDSDK;
    const pendingCodes = pendingAccountOauthProviderCodes();
    state.accountOauth.vkCodeVerifier = generateAccountVkCodeVerifier();
    state.accountOauth.vkState = String(config?.state || "");
    VKID.Config.init({
      app: Number(config?.app_id || 0),
      redirectUrl: String(config?.redirect_url || "https://kolbabot.ru"),
      responseMode: VKID.ConfigResponseMode.Callback,
      source: VKID.ConfigSource.LOWCODE,
      scope: "phone email",
      codeVerifier: state.accountOauth.vkCodeVerifier,
      state: state.accountOauth.vkState,
    });
    const providerConfigs = [
      { code: "vkid", sdkName: VKID.OAuthName.VK, host: el.accountVkProviderVk },
      { code: "mail_ru", sdkName: VKID.OAuthName.MAIL, host: el.accountVkProviderMail },
      { code: "ok_ru", sdkName: VKID.OAuthName.OK, host: el.accountVkProviderOk },
    ].filter((item) => pendingCodes.includes(item.code) && item.host);
    providerConfigs.forEach(({ code, sdkName, host }) => {
      const widget = new VKID.OAuthList();
      state.accountOauth.vkWidgets.push(widget);
      widget
        .render({ container: host, oauthList: [sdkName], styles: { height: 44, borderRadius: 8 } })
        .on(VKID.WidgetEvents.ERROR, (error) => {
          const detail = String(error?.error_msg || error?.error || error?.text || error?.message || "").trim();
          setResult(
            el.accountOauthResult,
            detail || `Не удалось открыть ${accountOauthProviderLabel(code)}. Попробуйте ещё раз.`,
            true,
          );
        })
        .on(VKID.OAuthListInternalEvents.LOGIN_SUCCESS, async (result) => {
          try {
            await completeAccountVkLink(result, code);
          } catch (error) {
            setResult(el.accountOauthResult, errorText(error, `Не удалось привязать ${accountOauthProviderLabel(code)}.`), true);
            await initAccountOauthProviders({ force: true });
          }
        });
      window.setTimeout(() => {
        if (!state.accountOauth.vkWidgets.includes(widget)) return;
        const caption = host.querySelector(".VkIdSdk_oauth_button_text");
        if (caption) caption.textContent = `Привязать ${accountOauthProviderLabel(code)}`;
        const action = host.querySelector("[data-oauth]");
        if (action) {
          action.setAttribute("role", "button");
          action.setAttribute("tabindex", "0");
          action.setAttribute("aria-label", `Привязать ${accountOauthProviderLabel(code)}`);
          action.addEventListener("keydown", (event) => {
            if (event.key !== "Enter" && event.key !== " ") return;
            event.preventDefault();
            action.click();
          });
        }
      }, 0);
    });
    state.accountOauth.vkProviderCodes = pendingCodes.join(",");
    state.accountOauth.vkInitialized = true;
    updateAccountOauthActionsState();
  } catch (error) {
    clearAccountOauthProviders();
    setResult(el.accountOauthResult, errorText(error, "Не удалось загрузить способы привязки."), true);
  }
}

async function loadAccountOauthConnections({ initializeProviders = false, forceProviderRefresh = false } = {}) {
  if (!state.isAuthenticated || !el.accountOauthCard) return;
  el.accountOauthCard.classList.remove("hidden");
  try {
    const data = await api("/api/account/oauth/connections");
    state.accountOauth.connections = Array.isArray(data?.connections) ? data.connections : [];
    state.accountOauth.loaded = true;
    renderAccountOauthConnections();
    if (initializeProviders) await initAccountOauthProviders({ force: forceProviderRefresh });
  } catch (error) {
    setResult(el.accountOauthResult, errorText(error, "Не удалось загрузить способы входа."), true);
  }
}

function startAccountGoogleLink() {
  if (!el.accountOauthConsent?.checked) {
    setResult(el.accountOauthResult, "Сначала подтвердите согласие на получение данных аккаунта.", true);
    return;
  }
  const params = new URLSearchParams({ mode: "link", profile_consent: "1", next: "/#account" });
  window.location.assign(`/api/auth/google/start?${params.toString()}`);
}

function handleOauthLinkReturn() {
  const url = new URL(window.location.href);
  const status = String(url.searchParams.get("oauth_link_status") || "");
  const message = String(url.searchParams.get("oauth_link_message") || "");
  if (!status) return;
  const isError = status !== "success";
  if (el.accountOauthCard) el.accountOauthCard.open = true;
  setResult(el.accountOauthResult, message || (isError ? "Не удалось привязать аккаунт." : "Способ входа привязан."), isError);
  showToast(message || (isError ? "Не удалось привязать аккаунт." : "Способ входа привязан."), isError);
  url.searchParams.delete("oauth_link_status");
  url.searchParams.delete("oauth_link_provider");
  url.searchParams.delete("oauth_link_message");
  window.history.replaceState(null, "", `${url.pathname}${url.search}${url.hash || "#account"}`);
}

function resetAccountEmailLinkUi({ clearEmail = false } = {}) {
  state.accountEmail.pendingEmail = "";
  state.accountEmail.verificationStarted = false;
  el.accountEmailRequestFields?.classList.remove("hidden");
  el.accountEmailVerifyFields?.classList.add("hidden");
  if (el.accountEmailInput) {
    el.accountEmailInput.disabled = false;
    if (clearEmail) el.accountEmailInput.value = "";
  }
  if (el.accountEmailCodeInput) el.accountEmailCodeInput.value = "";
  if (el.accountEmailPendingAddress) el.accountEmailPendingAddress.textContent = "";
}

function showAccountEmailVerification(email) {
  const normalizedEmail = String(email || "").trim().toLowerCase();
  state.accountEmail.pendingEmail = normalizedEmail;
  state.accountEmail.verificationStarted = true;
  if (el.accountEmailInput) {
    el.accountEmailInput.value = normalizedEmail;
    el.accountEmailInput.disabled = true;
  }
  if (el.accountEmailPendingAddress) el.accountEmailPendingAddress.textContent = normalizedEmail;
  el.accountEmailRequestFields?.classList.add("hidden");
  el.accountEmailVerifyFields?.classList.remove("hidden");
  el.accountEmailCodeInput?.focus();
}

function syncAccountEmailLinkUi() {
  if (!el.accountEmailLinkForm) return;
  const canLinkEmail = Boolean(state.isAuthenticated && !state.userEmailVerified);
  el.accountEmailLinkForm.classList.toggle("hidden", !canLinkEmail);
  if (!canLinkEmail) {
    resetAccountEmailLinkUi();
    return;
  }
  if (!state.accountEmail.verificationStarted && el.accountEmailInput && !el.accountEmailInput.value) {
    el.accountEmailInput.value = state.userEmail || "";
  }
}

async function startAccountEmailLink({ resend = false } = {}) {
  if (!state.isAuthenticated) return;
  const email = String(
    resend ? state.accountEmail.pendingEmail : el.accountEmailInput?.value || "",
  ).trim().toLowerCase();
  if (!email) {
    setResult(el.accountEmailResult, "Укажите email.", true);
    el.accountEmailInput?.focus();
    return;
  }
  if (!resend && el.accountEmailInput && !el.accountEmailInput.checkValidity()) {
    el.accountEmailInput.reportValidity();
    return;
  }

  const button = resend ? el.accountEmailResendBtn : el.accountEmailSendBtn;
  setButtonBusy(button, true, resend ? "Отправляем..." : "Отправляем код...");
  try {
    const data = await api("/api/account/email/start", { method: "POST", body: { email } });
    showAccountEmailVerification(data?.email || email);
    setResult(
      el.accountEmailResult,
      `Код отправлен. Он действует ${Number(data?.expires_in_minutes || 30)} минут.`,
    );
  } catch (error) {
    setResult(el.accountEmailResult, errorText(error, "Не удалось отправить код."), true);
  } finally {
    setButtonBusy(button, false);
  }
}

async function confirmAccountEmailLink() {
  const email = String(state.accountEmail.pendingEmail || "").trim().toLowerCase();
  const code = String(el.accountEmailCodeInput?.value || "").trim();
  if (!/^\d{6}$/.test(code)) {
    setResult(el.accountEmailResult, "Введите шестизначный код из письма.", true);
    el.accountEmailCodeInput?.focus();
    return;
  }

  setButtonBusy(el.accountEmailConfirmBtn, true, "Проверяем...");
  try {
    const data = await api("/api/account/email/confirm", { method: "POST", body: { email, code } });
    const user = data?.user || {};
    state.userEmail = String(user?.email || email);
    state.userEmailVerified = Boolean(user?.email_verified);
    resetAccountEmailLinkUi();
    renderAccountInfo();
    showToast("Email подтверждён и привязан к кабинету.");
  } catch (error) {
    setResult(el.accountEmailResult, errorText(error, "Не удалось подтвердить email."), true);
  } finally {
    setButtonBusy(el.accountEmailConfirmBtn, false);
  }
}

function accountReferralUrl() {
  const code = String(state.referralProgram?.code || "").trim().toLowerCase();
  if (!/^[a-z0-9]{8,32}$/.test(code)) return "";
  const url = new URL("/auth", window.location.origin);
  url.searchParams.set("tab", "register");
  url.searchParams.set("ref", code);
  return url.toString();
}

function bindAccountReferralCopy() {
  const button = document.getElementById("accountReferralCopyBtn");
  const input = document.getElementById("accountReferralLink");
  if (!button || !input) return;
  button.addEventListener("click", async () => {
    const previous = button.textContent || "Скопировать";
    button.disabled = true;
    button.textContent = "Копируем…";
    try {
      const copied = await copyTextToClipboard(input.value);
      if (!copied) throw new Error("copy_failed");
      button.textContent = "Скопировано";
      showToast("Реферальная ссылка скопирована.");
    } catch (_error) {
      input.focus();
      input.select();
      button.textContent = previous;
      showToast("Не удалось скопировать автоматически. Ссылка выделена.", true);
    } finally {
      window.setTimeout(() => {
        button.disabled = false;
        button.textContent = previous;
      }, 1400);
    }
  });
}

function renderAccountInfo() {
  if (!el.accountInfo) return;
  if (!state.isAuthenticated) {
    if (el.accountGreetingName) el.accountGreetingName.textContent = "гость";
    if (el.accountProfileState) {
      el.accountProfileState.classList.remove("is-success");
      el.accountProfileState.lastChild.textContent = "Гостевой режим";
    }
    setHtml(
      el.accountInfo,
      `<div class="account-profile-guest">
        <div class="account-profile-avatar" aria-hidden="true">?</div>
        <div>
          <h3>Вы пока не вошли</h3>
          <p>Войдите, чтобы сохранять расчёты, заказы и управлять подпиской.</p>
        </div>
      </div>
      <div class="account-profile-guest__actions">
        <a class="button-link" href="${escapeHtml(buildAuthUrl("login"))}">Войти</a>
        <a class="button-link account-secondary-link" href="${escapeHtml(buildAuthUrl("register"))}">Создать аккаунт</a>
      </div>`,
    );
    syncAccountEmailLinkUi();
    return;
  }
  const fullName = String(state.username || `${state.userFirstName} ${state.userLastName}`.trim() || "Пользователь").trim();
  const initials = fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("") || "К";
  const rawProvider = String(state.userOauthProvider || "").trim();
  const providerKey = rawProvider.toLowerCase().replaceAll("-", "_");
  const providerLabel = ({
    google: "Google",
    vk: "VK ID",
    vkid: "VK ID",
    mail: "Mail.ru",
    mail_ru: "Mail.ru",
    ok: "Одноклассники",
    ok_ru: "Одноклассники",
    local: "Email и пароль",
  })[providerKey] || rawProvider || "Email и пароль";
  if (el.accountGreetingName) el.accountGreetingName.textContent = state.userFirstName || fullName.split(/\s+/)[0] || "пользователь";
  if (el.accountProfileState) {
    el.accountProfileState.classList.add("is-success");
    el.accountProfileState.lastChild.textContent = "Аккаунт активен";
  }
  const emailLabel = state.userEmail
    ? `${state.userEmail}${state.userEmailVerified ? "" : " · не подтверждён"}`
    : "Не привязан";
  const referral = state.referralProgram || {};
  const referralUrl = accountReferralUrl();
  const referralPercent = Math.max(0, Math.min(100, Number(referral.percent || 0)));
  const referralCap = Math.max(0, Number(referral.max_reward_sausages || 2000));
  const invitedCount = Math.max(0, Number(referral.invited_count || 0));
  const referralStatus = referral.is_active
    ? `Друг зарегистрируется по ссылке — вы получите ${referralPercent}% колбасками с его первой покупки без доставки, максимум ${referralCap.toLocaleString("ru-RU")}.`
    : "Акция сейчас приостановлена администратором. Ваша персональная ссылка сохранена и не изменится.";
  setHtml(
    el.accountInfo,
    `<div class="account-profile-identity">
      <div class="account-profile-avatar" aria-hidden="true">${escapeHtml(initials)}</div>
      <div>
        <h3>${escapeHtml(fullName)}</h3>
        <p>${state.isAdminAccount ? "Администратор" : "Участник Колбабота"}</p>
      </div>
    </div>
    <dl class="account-profile-facts">
      <div>
        <dt>Email</dt>
        <dd>${escapeHtml(emailLabel)}</dd>
      </div>
      <div>
        <dt>Телефон</dt>
        <dd>${escapeHtml(state.userPhone || "Не указан")}</dd>
      </div>
      <div>
        <dt>Основной вход</dt>
        <dd>${escapeHtml(providerLabel)}</dd>
      </div>
    </dl>
    <div class="account-reward-balance">
      <span class="account-reward-balance__icon" aria-hidden="true">К</span>
      <span>
        <small>Баланс колбасок</small>
        <strong>${sausageCountText(Number((state.store.rewards || {}).sausages_kopecks || 0))}</strong>
      </span>
    </div>
    <div class="account-referral${referral.is_active ? " is-active" : ""}">
      <div class="account-referral__heading">
        <span>
          <small>Акция</small>
          <strong>Приведи друга</strong>
        </span>
        <b>${referral.is_active ? `${referralPercent}%` : "Пауза"}</b>
      </div>
      <p>${escapeHtml(referralStatus)}</p>
      <label class="account-referral__link">
        <span>Ваша ссылка на регистрацию</span>
        <span class="account-referral__copy-row">
          <input id="accountReferralLink" type="text" value="${escapeHtml(referralUrl)}" readonly aria-label="Реферальная ссылка на регистрацию">
          <button type="button" id="accountReferralCopyBtn" ${referralUrl ? "" : "disabled"}>Скопировать</button>
        </span>
      </label>
      <small class="account-referral__summary">Приглашено друзей: ${invitedCount.toLocaleString("ru-RU")}</small>
    </div>`,
  );
  bindAccountReferralCopy();
  syncAccountEmailLinkUi();
}
function bindAdminEvents() {
  el.adminDashboardRefreshBtn?.addEventListener("click", () => {
    void loadAdminDashboard();
  });
  el.adminSocialLinksSaveBtn?.addEventListener("click", () => {
    void saveAdminSiteSocialLinks();
  });
  el.adminMaintenanceForced?.addEventListener("change", () => {
    void saveAdminMaintenanceSettings(Boolean(el.adminMaintenanceForced?.checked));
  });
  el.adminMaintenanceSaveBtn?.addEventListener("click", () => {
    void saveAdminMaintenanceSettings();
  });
  el.adminRegistrationBonusSaveBtn?.addEventListener("click", () => {
    void saveRegistrationBonusSettings();
  });
  el.adminArticleSelect?.addEventListener("change", () => {
    fillAdminArticle(selectedAdminArticle() || {});
  });
  el.adminArticleNewBtn?.addEventListener("click", () => {
    if (el.adminArticleSelect) el.adminArticleSelect.value = "";
    fillAdminArticle({});
    el.adminArticleTitle?.focus();
  });
  el.adminArticleSaveBtn?.addEventListener("click", async () => {
    setButtonBusy(el.adminArticleSaveBtn, true, "Сохраняем…");
    try {
      await saveAdminArticle();
    } catch (error) {
      setResult(el.adminArticleResult, errorText(error, "Не удалось сохранить статью."), true);
    } finally {
      setButtonBusy(el.adminArticleSaveBtn, false);
    }
  });
  el.adminArticleDeleteBtn?.addEventListener("click", async () => {
    const article = selectedAdminArticle();
    if (!article) return;
    if (!window.confirm(`Удалить статью «${article.title || "Без заголовка"}» вместе с комментариями и оценками?`)) return;
    setButtonBusy(el.adminArticleDeleteBtn, true, "Удаляем…");
    try {
      await api(`/api/admin/articles/${article.id}`, { method: "DELETE", body: {} });
      state.admin.articles = (state.admin.articles || []).filter((item) => Number(item.id) !== Number(article.id));
      populateAdminArticleSelect();
      await loadArticles();
      showToast("Статья удалена.");
    } catch (error) {
      setResult(el.adminArticleResult, errorText(error, "Не удалось удалить статью."), true);
    } finally {
      setButtonBusy(el.adminArticleDeleteBtn, false);
    }
  });
  el.adminArticleCoverUploadBtn?.addEventListener("click", () => {
    openAdminArticleImageEditor({
      file: el.adminArticleCoverFile?.files?.[0],
      targetPreset: "cover",
    });
  });
  el.adminArticleCoverClearBtn?.addEventListener("click", () => {
    if (el.adminArticleCoverPath) el.adminArticleCoverPath.value = "";
    el.adminArticleCoverPreview?.classList.add("hidden");
    el.adminArticleCoverPreview?.removeAttribute("src");
  });

  el.adminArticleCoverEditorBtn?.addEventListener("click", () => {
    const file = el.adminArticleCoverFile?.files?.[0];
    const url = el.adminArticleCoverPreview?.src || el.adminArticleCoverPath?.value || "";
    openAdminArticleImageEditor({
      file,
      url,
      targetPreset: "cover",
    });
  });

  el.adminArticleCoverPreview?.addEventListener("click", () => {
    const url = el.adminArticleCoverPreview?.src || "";
    if (!url) return;
    openAdminArticleImageEditor({
      url,
      targetPreset: "cover",
    });
  });

  el.adminArticleCardImageUploadBtn?.addEventListener("click", () => {
    openAdminArticleImageEditor({
      file: el.adminArticleCardImageFile?.files?.[0],
      targetPreset: "avatar",
    });
  });
  el.adminArticleCardImageClearBtn?.addEventListener("click", () => {
    if (el.adminArticleCardImagePath) el.adminArticleCardImagePath.value = "";
    el.adminArticleCardImagePreview?.classList.add("hidden");
    el.adminArticleCardImagePreview?.removeAttribute("src");
    setResult(el.adminArticleResult, "Для карточки будет использоваться обложка. Нажмите «Сохранить».");
  });
  el.adminArticleCardImageEditorBtn?.addEventListener("click", () => {
    const file = el.adminArticleCardImageFile?.files?.[0];
    const url = el.adminArticleCardImagePreview?.src || el.adminArticleCardImagePath?.value || "";
    openAdminArticleImageEditor({ file, url, targetPreset: "avatar" });
  });
  el.adminArticleCardImagePreview?.addEventListener("click", () => {
    const url = el.adminArticleCardImagePreview?.src || "";
    if (url) openAdminArticleImageEditor({ url, targetPreset: "avatar" });
  });

  el.adminArticleInlineImageUploadBtn?.addEventListener("click", async () => {
    setButtonBusy(el.adminArticleInlineImageUploadBtn, true, "Загружаем…");
    try {
      const file = el.adminArticleInlineImageFile?.files?.[0];
      const uploaded = await uploadAdminArticleImage(file);
      const url = String(uploaded.photo_url || "").trim();
      if (!url) throw new Error("Сервер не вернул адрес изображения.");
      restoreArticleEditorSelection();
      document.execCommand(
        "insertHTML",
        false,
        `<figure><img src="${escapeHtml(url)}" alt="${escapeHtml(file?.name || "Фотография статьи")}" loading="lazy"><figcaption></figcaption></figure><p><br></p>`,
      );
      rememberArticleEditorSelection();
      if (el.adminArticleInlineImageFile) el.adminArticleInlineImageFile.value = "";
      setResult(el.adminArticleResult, "Фотография вставлена в текст. Нажмите «Сохранить».");
    } catch (error) {
      setResult(el.adminArticleResult, errorText(error, "Не удалось загрузить фотографию."), true);
    } finally {
      setButtonBusy(el.adminArticleInlineImageUploadBtn, false);
    }
  });

  el.adminArticleInlineImageEditorBtn?.addEventListener("click", () => {
    const file = el.adminArticleInlineImageFile?.files?.[0];
    openAdminArticleImageEditor({
      file,
      targetPreset: "inline",
    });
  });

  el.adminArticlePhotoEditorBtn?.addEventListener("mousedown", (event) => event.preventDefault());
  el.adminArticlePhotoEditorBtn?.addEventListener("click", () => {
    const inlineFile = el.adminArticleInlineImageFile?.files?.[0];
    const avatarFile = el.adminArticleCardImageFile?.files?.[0];
    const coverFile = el.adminArticleCoverFile?.files?.[0];
    const avatarUrl = el.adminArticleCardImagePreview?.src || "";
    const coverUrl = el.adminArticleCoverPreview?.src || "";
    const file = inlineFile || avatarFile || coverFile;
    const targetPreset = inlineFile ? "inline" : avatarFile ? "avatar" : coverFile ? "cover" : avatarUrl ? "avatar" : "cover";
    const sourceUrl = targetPreset === "avatar" ? avatarUrl : coverUrl;

    if (file || sourceUrl) {
      openAdminArticleImageEditor({ file, url: sourceUrl, targetPreset });
    } else {
      showToast("Выберите изображение для редактирования.", true);
    }
  });

  el.adminArticleEditor?.addEventListener("click", (e) => {
    if (e.target && e.target.tagName === "IMG") {
      const targetImg = e.target;
      openAdminArticleImageEditor({
        url: targetImg.src,
        targetPreset: "inline",
        inlineImage: targetImg,
      });
    }
  });

  el.adminArticleLinkBtn?.addEventListener("mousedown", (event) => event.preventDefault());
  el.adminArticleLinkBtn?.addEventListener("click", () => {
    const url = window.prompt("Введите адрес ссылки (https://…)", "https://");
    if (!url) return;
    runArticleEditorCommand("createLink", url.trim());
  });

  document.getElementById("adminStoreImportBtn")?.addEventListener("click", importAdminStore);
  document.getElementById("adminStoreRefreshDescriptionsBtn")?.addEventListener("click", refreshAdminStoreDescriptions);
  document.getElementById("adminStoreCategoryAddBtn")?.addEventListener("click", addAdminStoreCategory);
  document.getElementById("adminStoreCategoryNewTitle")?.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      void addAdminStoreCategory();
    }
  });
  document.getElementById("adminStoreCategorySelect")?.addEventListener("change", onAdminStoreCategorySelectChange);
  document.getElementById("adminStoreCategorySaveBtn")?.addEventListener("click", saveSelectedAdminStoreCategory);
  document.getElementById("adminStoreCategoryDeleteBtn")?.addEventListener("click", deleteSelectedAdminStoreCategory);
  document.getElementById("adminStoreNewBtn")?.addEventListener("click", () => fillAdminStore({}));
  document.getElementById("adminStoreRelatedAddBtn")?.addEventListener("click", addAdminStoreRelated);
  document.getElementById("adminStoreSaveBtn")?.addEventListener("click", saveAdminStore);
  document.getElementById('adminStoreVariantGroup')?.addEventListener('input',renderAdminStoreVariants);
  document.getElementById("adminStoreDeleteBtn")?.addEventListener("click", async () => { const id=document.getElementById("adminStoreProductId")?.value; if(id){ await api(`/api/admin/store/products/${id}`, {method:"DELETE", body:{}}); await refreshAdminStore(); }});
  el.adminOrdersCurrentBtn?.addEventListener("click", () => loadAdminOrders("current"));
  el.adminOrdersCompletedBtn?.addEventListener("click", () => loadAdminOrders("completed"));
  el.adminOrdersFailedBtn?.addEventListener("click", () => loadAdminOrders("failed"));
  el.adminOrdersDigitalBtn?.addEventListener("click", () => loadAdminOrders("digital"));
  document.getElementById('adminStoreProductType')?.addEventListener('change',updateAdminBookEditor);
  document.getElementById('adminStoreActive')?.addEventListener('change',renderAdminDigitalBookStatus);
  document.getElementById('adminBookSaveBtn')?.addEventListener('click',saveAdminStore);
  el.adminDigitalBookUploadBtn?.addEventListener("click", () => uploadAdminDigitalBook());
  el.adminDigitalBookPreviewBtn?.addEventListener("click", () => downloadAdminDigitalBookPreview());
  el.adminMobileBookUploadBtn?.addEventListener("click", () => uploadAdminDigitalBook('mobile'));
  el.adminMobileBookPreviewBtn?.addEventListener("click", () => downloadAdminDigitalBookPreview('mobile'));
  el.adminDigitalDeliveredBtn?.addEventListener("click", () => loadAdminDigitalSales("delivered"));
  el.adminDigitalAttentionBtn?.addEventListener("click", () => loadAdminDigitalSales("attention"));
  el.adminOrdersRefreshBtn?.addEventListener("click", () => loadAdminOrders(state.admin.ordersStatus || "current"));
  el.adminCartsRefreshBtn?.addEventListener("click", () => loadAdminCarts());
  document.getElementById('adminReviewsRefreshBtn')?.addEventListener('click',()=>loadAdminReviews());
  document.getElementById("accountOrdersRefresh")?.addEventListener("click", loadAccountOrders);
  if (!state.isAdmin) return;

  el.adminNotificationGlobalTab?.addEventListener("click", () => setAdminNotificationTab("global"));
  el.adminNotificationPersonalTab?.addEventListener("click", () => setAdminNotificationTab("personal"));
  el.adminNotificationGlobalSendBtn?.addEventListener("click", () => {
    void sendAdminNotification("global");
  });
  el.adminNotificationPersonalSendBtn?.addEventListener("click", () => {
    void sendAdminNotification("personal");
  });
  el.adminNotificationsRefreshBtn?.addEventListener("click", async () => {
    setButtonBusy(el.adminNotificationsRefreshBtn, true, "Обновляем...");
    try {
      await loadAdminNotifications();
      setResult(el.adminNotificationsResult, "История оповещений обновлена.");
    } catch (error) {
      setResult(el.adminNotificationsResult, errorText(error, "Не удалось обновить историю оповещений."), true);
    } finally {
      setButtonBusy(el.adminNotificationsRefreshBtn, false);
    }
  });

  el.adminCategorySelect.addEventListener("change", () => {
    syncAdminCategoryFields();
  });

  el.adminCategoryNewBtn.addEventListener("click", () => {
    el.adminCategorySelect.value = "";
    el.adminCategoryTitle.value = "";
    setResult(el.adminCatalogResult, "Новый режим: введите название категории и нажмите «Сохранить». ");
  });

  el.adminCategorySaveBtn.addEventListener("click", async () => {
    try {
      const title = el.adminCategoryTitle.value.trim();
      if (!title) throw new Error("Введите название категории.");

      const existingSlug = el.adminCategorySelect.value.trim();
      if (existingSlug) {
        await api(`/api/admin/categories/${encodeURIComponent(existingSlug)}`, {
          method: "PATCH",
          body: { user_id: state.userId, title },
        });
      } else {
        const taken = new Set((state.admin.categories || []).map((x) => x.slug));
        const slugBase = toSlug(title) || "category";
        const slug = uniqueSlug(slugBase, taken);
        await api("/api/admin/categories", {
          method: "POST",
          body: { user_id: state.userId, slug, title },
        });
      }

      await refreshAdminBootstrap();
      setResult(el.adminCatalogResult, "Категория сохранена.");
      showToast("Категория сохранена.");
    } catch (error) {
      setResult(el.adminCatalogResult, error.message, true);
    }
  });

  el.adminCategoryDeleteBtn.addEventListener("click", async () => {
    try {
      const slug = el.adminCategorySelect.value.trim();
      if (!slug) throw new Error("Выберите категорию для удаления.");
      if (!window.confirm("Удалить выбранную категорию?")) return;
      await api(`/api/admin/categories/${encodeURIComponent(slug)}`, {
        method: "DELETE",
        body: { user_id: state.userId },
      });
      await refreshAdminBootstrap();
      setResult(el.adminCatalogResult, "Категория удалена.");
      showToast("Категория удалена.");
    } catch (error) {
      setResult(el.adminCatalogResult, error.message, true);
    }
  });

  el.adminSausageCategorySelect.addEventListener("change", () => {
    syncAdminSausageSelect();
  });

  el.adminSausageSelect.addEventListener("change", () => {
    const category = (state.admin.categories || []).find((c) => c.slug === el.adminSausageCategorySelect.value);
    const sausage = (category?.sausages || []).find((s) => s.slug === el.adminSausageSelect.value);
    el.adminSausageName.value = sausage?.display_name || "";
  });

  el.adminSausageNewBtn.addEventListener("click", () => {
    el.adminSausageSelect.value = "";
    el.adminSausageName.value = "";
    setResult(el.adminCatalogResult, "Новый режим сорта: введите название и нажмите «Сохранить». ");
  });

  el.adminSausageSaveBtn.addEventListener("click", async () => {
    try {
      const categorySlug = el.adminSausageCategorySelect.value;
      const name = el.adminSausageName.value.trim();
      if (!categorySlug) throw new Error("Выберите категорию.");
      if (!name) throw new Error("Введите название сорта.");

      const category = (state.admin.categories || []).find((c) => c.slug === categorySlug);
      const existing = category?.sausages || [];
      const selectedOldSlug = el.adminSausageSelect.value.trim();

      if (selectedOldSlug) {
        const taken = new Set(existing.map((x) => x.slug).filter((x) => x !== selectedOldSlug));
        const baseSlug = toSlug(name) || "sausage";
        const newSlug = baseSlug === selectedOldSlug
          ? selectedOldSlug
          : uniqueSlug(baseSlug, taken);
        const saveResult = await api("/api/admin/sausages", {
          method: "PATCH",
          body: {
            user_id: state.userId,
            category_slug: categorySlug,
            old_sausage_slug: selectedOldSlug,
            new_display_name: name,
            new_sausage_slug: newSlug,
          },
        });
        state.admin.pendingRecipeSlug = String(saveResult?.new_sausage_slug || newSlug).trim();
      } else {
        const taken = new Set(existing.map((x) => x.slug));
        const slug = uniqueSlug(toSlug(name) || "sausage", taken);
        await api("/api/admin/sausages", {
          method: "POST",
          body: {
            user_id: state.userId,
            category_slug: categorySlug,
            display_name: name,
            sausage_slug: slug,
          },
        });
      }

      await refreshAdminBootstrap();
      const pendingRecipeSlug = String(state.admin.pendingRecipeSlug || "").trim();
      if (pendingRecipeSlug && el.adminRecipeSausageSelect) {
        const recipeCategory = (state.admin.categories || []).find((cat) =>
          (cat.sausages || []).some((item) => item.slug === pendingRecipeSlug),
        );
        if (recipeCategory) {
          el.adminRecipeCategorySelect.value = recipeCategory.slug;
          syncAdminRecipeSausageSelect();
          el.adminRecipeSausageSelect.value = pendingRecipeSlug;
          await loadAdminRecipe();
        }
        state.admin.pendingRecipeSlug = "";
      }
      setResult(el.adminCatalogResult, "Сорт сохранён.");
      showToast("Сорт сохранён.");
    } catch (error) {
      setResult(el.adminCatalogResult, error.message, true);
    }
  });

  el.adminSausageSelect.addEventListener("change", () => {
    refreshAdminDeleteButtons();
  });

  el.adminSausageDeleteBtn.addEventListener("click", async () => {
    try {
      if (isAdminCreateSelection(el.adminSausageSelect)) {
        throw new Error("Пункт «Новый сорт» нельзя удалить.");
      }
      const categorySlug = el.adminSausageCategorySelect.value;
      const sausageSlug = el.adminSausageSelect.value;
      if (!categorySlug || !sausageSlug) throw new Error("Выберите сорт для удаления.");
      if (!window.confirm("Удалить выбранный сорт?")) return;

      await api("/api/admin/sausages", {
        method: "DELETE",
        body: {
          user_id: state.userId,
          category_slug: categorySlug,
          sausage_slug: sausageSlug,
        },
      });

      await refreshAdminBootstrap();
      setResult(el.adminCatalogResult, "Сорт удалён.");
      showToast("Сорт удалён.");
    } catch (error) {
      setResult(el.adminCatalogResult, error.message, true);
    }
  });

  el.adminHandbookSectionSelect.addEventListener("change", () => {
    syncAdminHandbookArticles();
  });

  el.adminHandbookNewBtn.addEventListener("click", () => {
    el.adminHandbookArticleSelect.value = "";
    el.adminHandbookArticleKey.value = "";
    el.adminHandbookArticleTitle.value = "";
    el.adminHandbookArticleText.value = "";
    el.adminHandbookArticleLink.value = "";
    setResult(el.adminHandbookResult, "Новый режим: заполните поля и нажмите «Сохранить».");
  });

  el.adminHandbookSaveBtn.addEventListener("click", async () => {
    try {
      const sectionKey = String(el.adminHandbookSectionSelect.value || "").trim();
      const articleKey = toSlug(el.adminHandbookArticleKey.value);
      const title = String(el.adminHandbookArticleTitle.value || "").trim();
      const text = String(el.adminHandbookArticleText.value || "").trim();
      const link = String(el.adminHandbookArticleLink.value || "").trim();
      if (!sectionKey) throw new Error("Выберите раздел.");
      if (!articleKey) throw new Error("Укажите ключ статьи (slug).");
      if (!title) throw new Error("Введите заголовок.");
      if (!text) throw new Error("Введите текст статьи.");
      await api(`/api/admin/handbook/${encodeURIComponent(sectionKey)}/${encodeURIComponent(articleKey)}`, {
        method: "PUT",
        body: {
          user_id: state.userId,
          title,
          text,
          link,
        },
      });
      await refreshAdminBootstrap();
      const currentSection = state.admin.handbookSections.find((item) => item.key === sectionKey);
      const exists = (currentSection?.articles || []).some((item) => item.key === articleKey);
      if (exists) {
        el.adminHandbookArticleSelect.value = articleKey;
        syncAdminHandbookArticleFields();
      }
      setResult(el.adminHandbookResult, "Статья справочника сохранена.");
      showToast("Справочник обновлен.");
    } catch (error) {
      setResult(el.adminHandbookResult, error.message, true);
    }
  });

  el.adminHandbookArticleSelect.addEventListener("change", () => {
    syncAdminHandbookArticleFields();
    refreshAdminDeleteButtons();
  });

  el.adminHandbookDeleteBtn.addEventListener("click", async () => {
    try {
      if (isAdminCreateSelection(el.adminHandbookArticleSelect)) {
        throw new Error("Пункт «Новая статья» нельзя удалить.");
      }
      const sectionKey = String(el.adminHandbookSectionSelect.value || "").trim();
      const articleKey = String(el.adminHandbookArticleSelect.value || "").trim() || toSlug(el.adminHandbookArticleKey.value);
      if (!sectionKey || !articleKey) throw new Error("Выберите статью для удаления.");
      if (!window.confirm("Удалить выбранную статью справочника?")) return;
      await api(`/api/admin/handbook/${encodeURIComponent(sectionKey)}/${encodeURIComponent(articleKey)}`, {
        method: "DELETE",
        body: { user_id: state.userId },
      });
      await refreshAdminBootstrap();
      setResult(el.adminHandbookResult, "Статья справочника удалена.");
      showToast("Статья удалена.");
    } catch (error) {
      setResult(el.adminHandbookResult, error.message, true);
    }
  });

  el.adminRecipeCategorySelect.addEventListener("change", () => {
    syncAdminRecipeSausageSelect();
  });

  el.adminRecipeSausageSelect.addEventListener("change", async () => {
    if (isAdminCreateSelection(el.adminRecipeSausageSelect)) {
      clearAdminRecipeEditor();
      if (el.adminRecipeSlug) el.adminRecipeSlug.readOnly = false;
      setResult(el.adminRecipeResult, "Новый рецепт: введите название, заполните рецепт и сохраните.");
      refreshAdminDeleteButtons();
      return;
    }
    try {
      if (el.adminRecipeSlug) el.adminRecipeSlug.readOnly = true;
      await loadAdminRecipe();
      setResult(el.adminRecipeResult, "");
    } catch (error) {
      setResult(el.adminRecipeResult, error.message, true);
    }
    refreshAdminDeleteButtons();
  });

  el.adminRecipeName?.addEventListener("input", () => {
    if (!isAdminCreateSelection(el.adminRecipeSausageSelect)) return;
    const allSlugs = new Set(
      (state.admin.categories || []).flatMap((category) => (category.sausages || []).map((item) => item.slug)),
    );
    el.adminRecipeSlug.value = uniqueSlug(toSlug(el.adminRecipeName.value) || "recipe", allSlugs);
  });

  el.adminRecipeSlug?.addEventListener("blur", () => {
    if (!isAdminCreateSelection(el.adminRecipeSausageSelect)) return;
    el.adminRecipeSlug.value = toSlug(el.adminRecipeSlug.value) || toSlug(el.adminRecipeName?.value || "") || "recipe";
  });

  el.adminAddIngredientRow.addEventListener("click", async () => {
    try { await ensureAdminStoreProductsLoaded(); } catch (_error) { /* покажем пустой список ниже */ }
    el.adminIngredientsList.appendChild(makeIngredientRow());
  });

  if (el.adminRecipeUploadBtn) {
    el.adminRecipeUploadBtn.addEventListener("click", async () => {
      setButtonBusy(el.adminRecipeUploadBtn, true, "Загрузка...");
      try {
        const uploaded = await uploadAdminRecipePhoto();
        setResult(
          el.adminRecipeResult,
          `Фото загружено: ${uploaded?.photo_path || "путь не получен"}`,
        );
        showToast("Фото загружено.");
      } catch (error) {
        if (el.adminRecipePhotoUploadHint) {
          el.adminRecipePhotoUploadHint.textContent = error.message || "Не удалось загрузить фото.";
          el.adminRecipePhotoUploadHint.classList.add("error-text");
        }
        setResult(el.adminRecipeResult, error.message, true);
      } finally {
        setButtonBusy(el.adminRecipeUploadBtn, false);
      }
    });
  }

  el.adminSaveRecipeBtn.addEventListener("click", async () => {
    setButtonBusy(el.adminSaveRecipeBtn, true, "Сохраняем...");
    try {
      const categorySlug = el.adminRecipeCategorySelect.value.trim();
      const selectedSlug = el.adminRecipeSausageSelect.value.trim();
      const isNewRecipe = !selectedSlug;
      const displayName = el.adminRecipeName?.value?.trim() || "";
      const slug = isNewRecipe
        ? (toSlug(el.adminRecipeSlug?.value || displayName) || "")
        : selectedSlug;
      if (!categorySlug) throw new Error("Выберите категорию рецепта.");
      if (!displayName) throw new Error("Введите название рецепта.");
      if (!slug) throw new Error("Не удалось сформировать slug рецепта.");
      assertAdminRecipeLoaded(selectedSlug);

      if (isNewRecipe) {
        const allSlugs = new Set(
          (state.admin.categories || []).flatMap((category) => (category.sausages || []).map((item) => item.slug)),
        );
        if (allSlugs.has(slug)) throw new Error("Рецепт с таким slug уже существует. Измените slug.");
      }

      const ingredients = collectRecipeIngredients();
      const recipeLoadRequestId = state.admin.recipeLoadRequestId;

      if (el.adminRecipePhotoFile?.files?.[0]) {
        await uploadAdminRecipePhoto();
      }

      if (el.adminRecipeSausageSelect.value.trim() !== selectedSlug
          || el.adminRecipeCategorySelect.value.trim() !== categorySlug
          || state.admin.recipeLoadRequestId !== recipeLoadRequestId) {
        throw new Error("Выбранный рецепт изменился. Повторите сохранение.");
      }
      assertAdminRecipeLoaded(selectedSlug);

      await api(`/api/admin/recipes/${encodeURIComponent(slug)}`, {
        method: "PUT",
        body: {
          user_id: state.userId,
          category_slug: categorySlug,
          display_name: displayName,
          create_recipe: isNewRecipe,
          recipe_text: el.adminRecipeText.value,
          photo_path: el.adminRecipePhotoPath?.value.trim() || null,
          photo_id: el.adminRecipePhotoId.value.trim() || null,
          ingredients,
          replace_ingredients: true,
        },
      });

      await refreshAdminBootstrap();
      el.adminRecipeCategorySelect.value = categorySlug;
      syncAdminRecipeSausageSelect();
      el.adminRecipeSausageSelect.value = slug;
      if (el.adminRecipeSlug) el.adminRecipeSlug.readOnly = true;
      await loadAdminRecipe();
      setResult(el.adminRecipeResult, "Рецепт сохранён.");
      showToast("Рецепт сохранён.");
    } catch (error) {
      setResult(el.adminRecipeResult, error.message, true);
    } finally {
      setButtonBusy(el.adminSaveRecipeBtn, false);
    }
  });

  el.adminClearRecipeTextBtn.addEventListener("click", async () => {
    try {
      const slug = el.adminRecipeSausageSelect.value;
      if (!slug) throw new Error("Выберите рецепт.");
      if (!window.confirm("Очистить текст рецепта?")) return;

      await api(`/api/admin/recipes/${encodeURIComponent(slug)}/text`, {
        method: "DELETE",
        body: { user_id: state.userId },
      });

      el.adminRecipeText.value = "";
      setResult(el.adminRecipeResult, "Текст рецепта очищен.");
      showToast("Текст рецепта очищен.");
    } catch (error) {
      setResult(el.adminRecipeResult, error.message, true);
    }
  });

  el.adminClearRecipePhotoBtn.addEventListener("click", async () => {
    try {
      const slug = el.adminRecipeSausageSelect.value;
      if (!slug) throw new Error("Выберите рецепт.");
      if (!window.confirm("Очистить фото рецепта?")) return;

      await api(`/api/admin/recipes/${encodeURIComponent(slug)}/photo`, {
        method: "DELETE",
        body: { user_id: state.userId },
      });

      if (el.adminRecipePhotoPath) {
        el.adminRecipePhotoPath.value = "";
      }
      el.adminRecipePhotoId.value = "";
      if (el.adminRecipePhotoUploadHint) {
        el.adminRecipePhotoUploadHint.textContent = "Фото очищено.";
        el.adminRecipePhotoUploadHint.classList.remove("error-text");
      }
      if (el.adminRecipePhotoPreview) {
        el.adminRecipePhotoPreview.classList.add("hidden");
        el.adminRecipePhotoPreview.removeAttribute("src");
      }
      setResult(el.adminRecipeResult, "Фото очищено.");
      showToast("Фото очищено.");
    } catch (error) {
      setResult(el.adminRecipeResult, error.message, true);
    }
  });
  el.adminFaqNewBtn.addEventListener("click", () => {
    el.adminFaqSelect.value = "";
    el.adminFaqQuestion.value = "";
    el.adminFaqAnswer.value = "";
  });

  el.adminFaqSaveBtn.addEventListener("click", async () => {
    try {
      const question = el.adminFaqQuestion.value.trim();
      const answer = el.adminFaqAnswer.value.trim();
      if (!question || !answer) throw new Error("Заполните вопрос и ответ.");

      const id = Number(el.adminFaqSelect.value || 0);
      if (id > 0) {
        await api(`/api/admin/faq/${id}`, {
          method: "PATCH",
          body: { user_id: state.userId, question, answer },
        });
      } else {
        await api("/api/admin/faq", {
          method: "POST",
          body: { user_id: state.userId, question, answer },
        });
      }

      await refreshAdminBootstrap();
      setResult(el.adminFaqResult, "FAQ сохранён.");
      showToast("FAQ сохранён.");
    } catch (error) {
      setResult(el.adminFaqResult, error.message, true);
    }
  });

  el.adminFaqSelect.addEventListener("change", () => {
    syncAdminFaqFields();
    refreshAdminDeleteButtons();
  });

  el.adminFaqDeleteBtn.addEventListener("click", async () => {
    try {
      if (isAdminCreateSelection(el.adminFaqSelect)) {
        throw new Error("Пункт «Новая запись» нельзя удалить.");
      }
      const id = Number(el.adminFaqSelect.value || 0);
      if (id <= 0) throw new Error("Выберите запись FAQ для удаления.");
      if (!window.confirm("Удалить выбранный FAQ?")) return;

      await api(`/api/admin/faq/${id}`, {
        method: "DELETE",
        body: { user_id: state.userId },
      });

      await refreshAdminBootstrap();
      setResult(el.adminFaqResult, "FAQ удалён.");
      showToast("FAQ удалён.");
    } catch (error) {
      setResult(el.adminFaqResult, error.message, true);
    }
  });

  el.adminEquipmentSelect.addEventListener("change", () => {
    syncAdminEquipmentFields();
    refreshAdminDeleteButtons();
  });

  el.adminEquipmentNewBtn.addEventListener("click", () => {
    el.adminEquipmentSelect.value = "";
    clearEquipmentFields();
  });

  el.adminEquipmentUploadBtn.addEventListener("click", async () => {
    setButtonBusy(el.adminEquipmentUploadBtn, true, "Загрузка...");
    try {
      const uploaded = await uploadAdminEquipmentPhoto();
      setResult(
        el.adminEquipmentResult,
        `Фото загружено: ${uploaded?.photo_path || "путь не получен"}`,
      );
      showToast("Фото загружено.");
    } catch (error) {
      if (el.adminEquipmentPhotoUploadHint) {
        el.adminEquipmentPhotoUploadHint.textContent = error.message || "Не удалось загрузить фото.";
        el.adminEquipmentPhotoUploadHint.classList.add("error-text");
      }
      setResult(el.adminEquipmentResult, error.message, true);
    } finally {
      setButtonBusy(el.adminEquipmentUploadBtn, false);
    }
  });

  el.adminEquipmentSaveBtn.addEventListener("click", async () => {
    setButtonBusy(el.adminEquipmentSaveBtn, true, "Сохраняем...");
    try {
      const categoryName = el.adminEquipmentCategoryName.value.trim();
      const title = el.adminEquipmentTitle.value.trim();
      const description = el.adminEquipmentDescription.value.trim();
      if (!categoryName || !title || !description) {
        throw new Error("Заполните категорию, название и описание.");
      }

      if (el.adminEquipmentPhotoFile?.files?.[0]) {
        await uploadAdminEquipmentPhoto();
      }

      const body = {
        user_id: state.userId,
        category_slug: toSlug(categoryName) || "equipment",
        category_name: categoryName,
        title,
        description,
        buy_link: el.adminEquipmentBuyLink.value.trim(),
        buy_label: el.adminEquipmentBuyLabel.value.trim() || "Купить",
        photo_path: el.adminEquipmentPhotoPath.value.trim(),
        photo_id: el.adminEquipmentPhotoId.value.trim() || null,
        sort_order: Number(el.adminEquipmentSortOrder.value || 100),
        is_active: Boolean(el.adminEquipmentActive.checked),
      };

      const id = Number(el.adminEquipmentSelect.value || 0);
      if (id > 0) {
        await api(`/api/admin/equipment/${id}`, {
          method: "PATCH",
          body,
        });
      } else {
        await api("/api/admin/equipment", {
          method: "POST",
          body,
        });
      }

      await refreshAdminBootstrap();
      setResult(el.adminEquipmentResult, "Оборудование сохранено.");
      showToast("Оборудование сохранено.");
    } catch (error) {
      setResult(el.adminEquipmentResult, error.message, true);
    } finally {
      setButtonBusy(el.adminEquipmentSaveBtn, false);
    }
  });

  el.adminEquipmentDeleteBtn.addEventListener("click", async () => {
    try {
      if (isAdminCreateSelection(el.adminEquipmentSelect)) {
        throw new Error("Пункт «Новая позиция» нельзя удалить.");
      }
      const id = Number(el.adminEquipmentSelect.value || 0);
      if (id <= 0) throw new Error("Выберите позицию оборудования.");
      if (!window.confirm("Удалить выбранную позицию оборудования?")) return;

      await api(`/api/admin/equipment/${id}`, {
        method: "DELETE",
        body: { user_id: state.userId },
      });

      await refreshAdminBootstrap();
      setResult(el.adminEquipmentResult, "Позиция удалена.");
      showToast("Позиция удалена.");
    } catch (error) {
      setResult(el.adminEquipmentResult, error.message, true);
    }
  });

  el.adminReloadTickets.addEventListener("click", async () => {
    try {
      await refreshAdminTickets();
      setResult(el.adminSupportResult, "Список обращений обновлён.");
      showToast("Список обращений обновлён.");
    } catch (error) {
      setResult(el.adminSupportResult, error.message, true);
    }
  });

  el.adminTicketSelect.addEventListener("change", syncAdminTicketDetails);

  el.adminSendTicketAnswer.addEventListener("click", async () => {
    setButtonBusy(el.adminSendTicketAnswer, true, "Отправляем...");
    try {
      const ticketId = Number(el.adminTicketSelect.value || 0);
      const answerText = el.adminTicketAnswer.value.trim();
      if (ticketId <= 0) throw new Error("Выберите обращение.");
      if (answerText.length < 2) throw new Error("Введите текст ответа.");

      await api(`/api/admin/support/tickets/${ticketId}/answer`, {
        method: "POST",
        body: {
          user_id: state.userId,
          answer_text: answerText,
        },
      });

      el.adminTicketAnswer.value = "";
      await refreshAdminTickets();
      setResult(el.adminSupportResult, `Ответ по обращению #${ticketId} сохранён.`);
      showToast(`Ответ по обращению #${ticketId} сохранён.`);
    } catch (error) {
      setResult(el.adminSupportResult, error.message, true);
    } finally {
      setButtonBusy(el.adminSendTicketAnswer, false);
    }
  });

  el.adminUsersSelect.addEventListener("change", syncAdminUserFields);

  const refreshUsersAfterAction = async (userId = 0) => {
    await refreshAdminBootstrap();
    if (userId > 0 && el.adminUsersSelect) {
      el.adminUsersSelect.value = String(userId);
    }
    syncAdminUserFields();
  };

  if (el.adminSaveUserBtn) {
    el.adminSaveUserBtn.addEventListener("click", async () => {
      setButtonBusy(el.adminSaveUserBtn, true, "Сохраняем...");
      try {
        const { userId, user } = getSelectedAdminUser();

        const nextIsAdmin = Boolean(el.adminUserIsAdmin?.checked);
        const nextIsActive = Boolean(el.adminUserIsActive?.checked);

        if (user.is_admin && !nextIsAdmin) {
          if (!window.confirm("Снять права администратора с пользователя?")) return;
        }
        if (user.is_active && !nextIsActive) {
          const blockReason = getAdminBanBlockReason(user);
          if (blockReason) throw new Error(blockReason);
          if (!window.confirm("Отключить пользователя? Он не сможет входить.")) return;
        }

        const username = String(el.adminUserUsername?.value || "").trim();
        if (!username) throw new Error("Введите логин пользователя.");
        const password = el.adminUserPassword?.value || "";
        const storeSausages = Math.max(0, Math.trunc(Number(el.adminUserStoreSausages?.value || 0)));
        await api(`/api/admin/users/${userId}`, {
          method: "PATCH",
          body: {
            user_id: state.userId,
            username,
            is_admin: nextIsAdmin,
            is_active: nextIsActive,
            password: password || null,
            store_sausages: storeSausages,
          },
        });

        if (el.adminUserPassword) {
          el.adminUserPassword.value = "";
        }
        await refreshUsersAfterAction(userId);
        setResult(el.adminAdminsResult, `Пользователь #${userId} обновлён.`);
        showToast("Пользователь обновлён.");
      } catch (error) {
        setResult(el.adminAdminsResult, error.message, true);
      } finally {
        setButtonBusy(el.adminSaveUserBtn, false);
      }
    });
  }

  el.adminRefreshAdminsBtn.addEventListener("click", async () => {
    setButtonBusy(el.adminRefreshAdminsBtn, true, "Обновляем...");
    try {
      await refreshUsersAfterAction();
      setResult(el.adminAdminsResult, "Список пользователей обновлен.");
    } catch (error) {
      setResult(el.adminAdminsResult, error.message, true);
    } finally {
      setButtonBusy(el.adminRefreshAdminsBtn, false);
    }
  });

  if (el.adminApplyUserAccessBtn) {
    el.adminApplyUserAccessBtn.addEventListener("click", async () => {
      setButtonBusy(el.adminApplyUserAccessBtn, true, "Применяем...");
      try {
        const { userId } = getSelectedAdminUser();
        const tier = String(el.adminUserAccessTier?.value || "").trim().toLowerCase();
        const mode = String(el.adminUserAccessMode?.value || "replace").trim().toLowerCase();
        const hours = Number(el.adminUserAccessHours?.value || 0);
        if (tier !== "premium") {
          throw new Error("Выберите корректный тариф.");
        }
        if (!Number.isFinite(hours) || hours <= 0) {
          throw new Error("Укажите duration_hours больше 0.");
        }
        if (!window.confirm(
          mode === "extend"
            ? `Продлить доступ пользователю #${userId} на ${hours} ч. (${humanTierTitle(tier)})?`
            : `Выдать доступ пользователю #${userId}: ${humanTierTitle(tier)} на ${hours} ч.?`,
        )) return;

        await api(`/api/admin/users/${userId}/access`, {
          method: "POST",
          body: {
            user_id: state.userId,
            tier,
            duration_hours: Math.round(hours),
            action: mode === "extend" ? "extend" : "replace",
          },
        });
        await refreshUsersAfterAction(userId);
        const verb = mode === "extend" ? "продлён" : "выдан";
        setResult(el.adminAdminsResult, `Доступ ${verb}: ${humanTierTitle(tier)} на ${Math.round(hours)} ч.`);
        showToast(`Доступ ${verb}.`);
      } catch (error) {
        setResult(el.adminAdminsResult, error.message, true);
      } finally {
        setButtonBusy(el.adminApplyUserAccessBtn, false);
      }
    });
  }

  if (el.adminRevokeAccessBtn) {
    el.adminRevokeAccessBtn.addEventListener("click", async () => {
      setButtonBusy(el.adminRevokeAccessBtn, true, "Снимаем...");
      try {
        const { userId, user } = getSelectedAdminUser();
        if (!window.confirm(`Снять доступ у ${user.username || `#${userId}`} и отключить автоплатёж?`)) return;
        await api(`/api/admin/users/${userId}/access/revoke`, {
          method: "POST",
          body: { user_id: state.userId },
        });
        await refreshUsersAfterAction(userId);
        setResult(el.adminAdminsResult, `Доступ снят для ${user.username || `#${userId}`}.`);
        showToast("Доступ снят.");
      } catch (error) {
        setResult(el.adminAdminsResult, error.message, true);
      } finally {
        setButtonBusy(el.adminRevokeAccessBtn, false);
      }
    });
  }

  if (el.adminDisableUserAutopayBtn) {
    el.adminDisableUserAutopayBtn.addEventListener("click", async () => {
      setButtonBusy(el.adminDisableUserAutopayBtn, true, "Отключаем...");
      try {
        const { userId, user } = getSelectedAdminUser();
        if (!window.confirm(`Отключить автоплатёж и отвязать токены у ${user.username || `#${userId}`}?`)) return;
        await api(`/api/admin/users/${userId}/autopay/disable`, {
          method: "POST",
          body: { user_id: state.userId },
        });
        await refreshUsersAfterAction(userId);
        setResult(el.adminAdminsResult, `Автоплатёж отключён: ${user.username || `#${userId}`}.`);
        showToast("Автоплатёж отключён.");
      } catch (error) {
        setResult(el.adminAdminsResult, error.message, true);
      } finally {
        setButtonBusy(el.adminDisableUserAutopayBtn, false);
      }
    });
  }

  if (el.adminBanUserBtn) {
    el.adminBanUserBtn.addEventListener("click", async () => {
      setButtonBusy(el.adminBanUserBtn, true, "Баним...");
      try {
        const { userId, user } = getSelectedAdminUser();
        const blockReason = getAdminBanBlockReason(user);
        if (blockReason) throw new Error(blockReason);
        if (!window.confirm(`Забанить ${user.username || `#${userId}`}? Вход будет недоступен.`)) return;
        await api(`/api/admin/users/${userId}`, {
          method: "PATCH",
          body: {
            user_id: state.userId,
            is_active: false,
          },
        });
        await refreshUsersAfterAction(userId);
        setResult(el.adminAdminsResult, `Пользователь забанен: ${user.username || `#${userId}`}.`);
        showToast("Пользователь забанен.");
      } catch (error) {
        setResult(el.adminAdminsResult, error.message, true);
      } finally {
        setButtonBusy(el.adminBanUserBtn, false);
        const selectedUserId = Number(el.adminUsersSelect?.value || 0);
        const selectedUser = (state.admin.users || []).find((item) => Number(item.id || 0) === selectedUserId);
        syncAdminBanProtectionState(selectedUser);
      }
    });
  }

  if (el.adminUnbanUserBtn) {
    el.adminUnbanUserBtn.addEventListener("click", async () => {
      setButtonBusy(el.adminUnbanUserBtn, true, "Разбан...");
      try {
        const { userId, user } = getSelectedAdminUser();
        await api(`/api/admin/users/${userId}`, {
          method: "PATCH",
          body: {
            user_id: state.userId,
            is_active: true,
          },
        });
        await refreshUsersAfterAction(userId);
        setResult(el.adminAdminsResult, `Пользователь разбанен: ${user.username || `#${userId}`}.`);
        showToast("Пользователь разбанен.");
      } catch (error) {
        setResult(el.adminAdminsResult, error.message, true);
      } finally {
        setButtonBusy(el.adminUnbanUserBtn, false);
      }
    });
  }

  if (el.adminRevokeUserSessionsBtn) {
    el.adminRevokeUserSessionsBtn.addEventListener("click", async () => {
      setButtonBusy(el.adminRevokeUserSessionsBtn, true, "Завершаем...");
      try {
        const { userId, user } = getSelectedAdminUser();
        if (!window.confirm(`Завершить все активные сессии пользователя ${user.username || `#${userId}`}?`)) return;
        await api(`/api/admin/users/${userId}/sessions/revoke`, {
          method: "POST",
          body: { user_id: state.userId },
        });
        await refreshUsersAfterAction(userId);
        setResult(el.adminAdminsResult, `Все сессии завершены: ${user.username || `#${userId}`}.`);
        showToast("Сессии завершены.");
      } catch (error) {
        setResult(el.adminAdminsResult, error.message, true);
      } finally {
        setButtonBusy(el.adminRevokeUserSessionsBtn, false);
      }
    });
  }

  el.adminGrantAdminBtn.addEventListener("click", async () => {
    setButtonBusy(el.adminGrantAdminBtn, true, "Сохраняем...");
    try {
      const username = el.adminNewAdminUsername.value.trim();
      const password = el.adminNewAdminPassword.value;
      if (!username) {
        throw new Error("Введите логин пользователя.");
      }
      const payload = await api("/api/admin/users/promote", {
        method: "POST",
        body: {
          user_id: state.userId,
          username,
          password,
        },
      });
      el.adminNewAdminPassword.value = "";
      const promotedUserId = Number(payload?.user?.id || 0) || 0;
      await refreshUsersAfterAction(promotedUserId);
      const updatedUsername = String(payload?.user?.username || username);
      if (el.adminUsersInfo) {
        el.adminUsersInfo.textContent = `Права админа выданы: ${updatedUsername}`;
      }
      setResult(el.adminAdminsResult, `Права админа выданы: ${updatedUsername}`);
      showToast(`Администратор обновлен: ${updatedUsername}`);
    } catch (error) {
      setResult(el.adminAdminsResult, error.message, true);
    } finally {
      setButtonBusy(el.adminGrantAdminBtn, false);
    }
  });

  if (el.adminRevokeAdminBtn) {
    el.adminRevokeAdminBtn.addEventListener("click", async () => {
      setButtonBusy(el.adminRevokeAdminBtn, true, "Снимаем...");
      try {
        const { userId, user } = getSelectedAdminUser();
        if (!user.is_admin) {
          throw new Error("У пользователя уже нет прав администратора.");
        }
        if (!window.confirm(`Снять права администратора с ${user.username || `#${userId}`}?`)) return;

        await api(`/api/admin/users/${userId}`, {
          method: "PATCH",
          body: {
            user_id: state.userId,
            is_admin: false,
          },
        });
        await refreshUsersAfterAction(userId);
        setResult(el.adminAdminsResult, `Права администратора сняты: ${user.username || `#${userId}`}.`);
        showToast("Права администратора сняты.");
      } catch (error) {
        setResult(el.adminAdminsResult, error.message, true);
      } finally {
        setButtonBusy(el.adminRevokeAdminBtn, false);
      }
    });
  }

  if (el.adminRunUserRenewalBtn) {
    el.adminRunUserRenewalBtn.addEventListener("click", async () => {
      setButtonBusy(el.adminRunUserRenewalBtn, true, "Запускаем...");
      try {
        const { userId, user } = getSelectedAdminUser();
        const userLabel = user?.username ? `${user.username} (#${userId})` : `#${userId}`;
        if (!window.confirm(`Запустить ПРИНУДИТЕЛЬНОЕ автосписание для ${userLabel}?`)) return;

        const result = await api("/api/admin/payments/tbank/renewals/run", {
          method: "POST",
          body: {
            user_id: userId,
            limit: 1,
            force: true,
          },
        });

        const processed = Number(result?.processed || 0);
        const success = Number(result?.success || 0);
        const failed = Number(result?.failed || 0);
        const item = Array.isArray(result?.items) ? result.items[0] : null;
        const orderId = String(item?.order_id || "").trim();
        const paymentId = String(item?.payment_id || "").trim();
        const statusText = String(item?.status || "").trim();
        const errorTextValue = String(item?.error || "").trim();
        const details = [
          orderId ? `order_id=${orderId}` : "",
          paymentId ? `payment_id=${paymentId}` : "",
          statusText ? `status=${statusText}` : "",
          errorTextValue ? `error=${errorTextValue}` : "",
        ].filter(Boolean).join("; ");
        const summary = `Принудительный тест автосписания для #${userId}: обработано ${processed}, успешно ${success}, ошибок ${failed}.${details ? ` ${details}` : ""}`;
        setResult(el.adminAdminsResult, summary, failed > 0);
        showToast(summary, failed > 0);
        try {
          await loadAdminUserDossier(userId);
        } catch (_dossierError) {
          // Ошибка досье не должна маскировать результат принудительного автосписания.
        }
      } catch (error) {
        setResult(el.adminAdminsResult, error.message, true);
      } finally {
        setButtonBusy(el.adminRunUserRenewalBtn, false);
      }
    });
  }

  if (el.adminDbApplyBtn) {
    el.adminDbApplyBtn.addEventListener("click", async () => {
      setButtonBusy(el.adminDbApplyBtn, true, "Загружаем...");
      try {
        state.admin.dbLog.offset = 0;
        await loadAdminDbLog();
      } catch (error) {
        setResult(el.adminDbLogList, error.message || "Не удалось загрузить события БД.", true);
      } finally {
        setButtonBusy(el.adminDbApplyBtn, false);
      }
    });
  }

  if (el.adminDbResetBtn) {
    el.adminDbResetBtn.addEventListener("click", async () => {
      stopAdminDbAutoRefresh();
      if (el.adminDbSourceFilter) el.adminDbSourceFilter.value = "all";
      if (el.adminDbLevelFilter) el.adminDbLevelFilter.value = "all";
      if (el.adminDbUserIdFilter) el.adminDbUserIdFilter.value = "";
      if (el.adminDbSearchFilter) el.adminDbSearchFilter.value = "";
      if (el.adminDbLimitFilter) el.adminDbLimitFilter.value = "100";
      if (el.adminDbAutoRefresh) el.adminDbAutoRefresh.checked = false;
      setButtonBusy(el.adminDbResetBtn, true, "Сбрасываем...");
      try {
        state.admin.dbLog.offset = 0;
        await loadAdminDbLog();
      } catch (error) {
        setResult(el.adminDbLogList, error.message || "Не удалось сбросить фильтры журнала.", true);
      } finally {
        setButtonBusy(el.adminDbResetBtn, false);
      }
    });
  }

  if (el.adminDbCopyBtn) {
    el.adminDbCopyBtn.addEventListener("click", async () => {
      const payload = {
        meta: {
          total: state.admin.dbLog.total,
          limit: state.admin.dbLog.limit,
          offset: state.admin.dbLog.offset,
          filters: state.admin.dbLog.lastFilters || {},
        },
        items: state.admin.dbLog.items || [],
      };
      try {
        await navigator.clipboard.writeText(JSON.stringify(payload, null, 2));
        showToast("JSON журнала скопирован в буфер.");
      } catch (_error) {
        showToast("Не удалось скопировать JSON журнала.", true);
      }
    });
  }

  if (el.adminDbLoadDossierBtn) {
    el.adminDbLoadDossierBtn.addEventListener("click", async () => {
      const filterUserId = Number(el.adminDbUserIdFilter?.value || 0) || 0;
      const selectedUserId = Number(el.adminUsersSelect?.value || 0) || 0;
      const targetUserId = filterUserId > 0 ? filterUserId : selectedUserId;
      if (targetUserId <= 0) {
        setResult(el.adminUserDossier, "Укажите user_id в фильтре или выберите пользователя в разделе «Пользователи».", true);
        return;
      }
      setButtonBusy(el.adminDbLoadDossierBtn, true, "Загружаем...");
      try {
        await loadAdminUserDossier(targetUserId);
      } catch (error) {
        setResult(el.adminUserDossier, error.message || "Не удалось загрузить личное дело.", true);
      } finally {
        setButtonBusy(el.adminDbLoadDossierBtn, false);
      }
    });
  }

  if (el.adminDbPresets) {
    el.adminDbPresets.addEventListener("click", async (event) => {
      const btn = event.target?.closest?.("[data-db-preset]");
      if (!btn) return;
      const preset = String(btn.dataset.dbPreset || "").trim().toLowerCase();
      applyDbPreset(preset);
      state.admin.dbLog.offset = 0;
      await loadAdminDbLog();
    });
  }

  if (el.adminDbPrevBtn) {
    el.adminDbPrevBtn.addEventListener("click", async () => {
      const nextOffset = Math.max(0, Number(state.admin.dbLog.offset || 0) - Number(state.admin.dbLog.limit || 100));
      const filters = readAdminDbLogFilters();
      filters.offset = nextOffset;
      await loadAdminDbLog(filters);
    });
  }

  if (el.adminDbNextBtn) {
    el.adminDbNextBtn.addEventListener("click", async () => {
      const nextOffset = Number(state.admin.dbLog.offset || 0) + Number(state.admin.dbLog.limit || 100);
      const filters = readAdminDbLogFilters();
      filters.offset = nextOffset;
      await loadAdminDbLog(filters);
    });
  }

  if (el.adminDbAutoRefresh) {
    el.adminDbAutoRefresh.addEventListener("change", () => {
      if (el.adminDbAutoRefresh.checked) {
        startAdminDbAutoRefresh();
      } else {
        stopAdminDbAutoRefresh();
      }
    });
  }

  if (el.adminDbSearchFilter) {
    el.adminDbSearchFilter.addEventListener("keydown", async (event) => {
      if (event.key !== "Enter") return;
      event.preventDefault();
      state.admin.dbLog.offset = 0;
      await loadAdminDbLog();
    });
  }

  if (el.adminResetUser2faBtn) {
    el.adminResetUser2faBtn.addEventListener("click", async () => {
      setButtonBusy(el.adminResetUser2faBtn, true, "Отвязываем...");
      try {
        const { userId, user } = getSelectedAdminUser();
        if (!user.totp_enabled && !user.twofa_enrolled) {
          throw new Error("У пользователя нет привязанной 2FA.");
        }
        if (!window.confirm(`Отвязать 2FA у ${user.username || `#${userId}`}?`)) return;

        await api(`/api/admin/users/${userId}`, {
          method: "PATCH",
          body: {
            user_id: state.userId,
            reset_2fa: true,
          },
        });
        await refreshUsersAfterAction(userId);
        setResult(el.adminAdminsResult, `2FA отвязан для: ${user.username || `#${userId}`}.`);
        showToast("2FA отвязан.");
      } catch (error) {
        setResult(el.adminAdminsResult, error.message, true);
      } finally {
        setButtonBusy(el.adminResetUser2faBtn, false);
      }
    });
  }

  if (el.adminCreatePromoBtn) {
    el.adminCreatePromoBtn.addEventListener("click", async () => {
      setButtonBusy(el.adminCreatePromoBtn, true, "Создаем...");
      try {
        const code = String(el.adminPromoCodeInput?.value || "").trim().toUpperCase();
        const tier = String(el.adminPromoTierSelect?.value || "").trim().toLowerCase();
        const discountPercent = Number(el.adminPromoDiscountInput?.value || 0);
        if (!code) {
          throw new Error("Введите код промокода.");
        }
        if (tier !== "premium") {
          throw new Error("Выберите корректный тариф.");
        }
        if (!Number.isFinite(discountPercent) || discountPercent < 0 || discountPercent > 99) {
          throw new Error("Скидка должна быть в диапазоне 0..99.");
        }
        await api("/api/admin/promocodes", {
          method: "POST",
          body: {
            user_id: state.userId,
            code,
            tier,
            discount_percent: discountPercent,
          },
        });
        if (el.adminPromoCodeInput) {
          el.adminPromoCodeInput.value = "";
        }
        await refreshAdminBootstrap();
        showToast("Промокод создан.");
      } catch (error) {
        setResult(el.adminImportResult, error.message, true);
      } finally {
        setButtonBusy(el.adminCreatePromoBtn, false);
      }
    });
  }

  el.adminRefreshStats.addEventListener("click", async () => {
    setButtonBusy(el.adminRefreshStats, true, "Обновляем...");
    try {
      const scope = el.adminStatsScope.value || "all";
      const data = await api(`/api/admin/stats?scope=${encodeURIComponent(scope)}&user_id=${encodeURIComponent(state.userId)}`);
      setHtml(el.adminStatsSummary, `
        <div>Клики: <strong>${data.total_clicks}</strong></div>
        <div>Пользователи: <strong>${data.unique_users}</strong></div>
        <div>Кнопки: <strong>${data.unique_buttons}</strong></div>
        <div>Строк отчёта: <strong>${data.rows}</strong></div>
      `);
    } catch (error) {
      setResult(el.adminStatsSummary, error.message, true);
    } finally {
      setButtonBusy(el.adminRefreshStats, false);
    }
  });

  if (el.adminAuditApplyBtn) {
    el.adminAuditApplyBtn.addEventListener("click", async () => {
      state.admin.audit.offset = 0;
      try {
        await loadAdminAudit();
      } catch (error) {
        setResult(el.adminAuditMeta, error.message, true);
      }
    });
  }

  if (el.adminAuditResetBtn) {
    el.adminAuditResetBtn.addEventListener("click", async () => {
      if (el.adminAuditMethod) el.adminAuditMethod.value = "";
      if (el.adminAuditStatusFrom) el.adminAuditStatusFrom.value = "";
      if (el.adminAuditStatusTo) el.adminAuditStatusTo.value = "";
      if (el.adminAuditPath) el.adminAuditPath.value = "";
      if (el.adminAuditIp) el.adminAuditIp.value = "";
      if (el.adminAuditAdminUserId) el.adminAuditAdminUserId.value = "";
      if (el.adminAuditRequestId) el.adminAuditRequestId.value = "";
      if (el.adminAuditCreatedFrom) el.adminAuditCreatedFrom.value = "";
      if (el.adminAuditCreatedTo) el.adminAuditCreatedTo.value = "";
      state.admin.audit.offset = 0;
      try {
        await loadAdminAudit();
      } catch (error) {
        setResult(el.adminAuditMeta, error.message, true);
      }
    });
  }

  if (el.adminAuditPrevBtn) {
    el.adminAuditPrevBtn.addEventListener("click", async () => {
      state.admin.audit.offset = Math.max(0, Number(state.admin.audit.offset || 0) - Number(state.admin.audit.limit || 100));
      try {
        await loadAdminAudit();
      } catch (error) {
        setResult(el.adminAuditMeta, error.message, true);
      }
    });
  }

  if (el.adminAuditNextBtn) {
    el.adminAuditNextBtn.addEventListener("click", async () => {
      const next = Number(state.admin.audit.offset || 0) + Number(state.admin.audit.limit || 100);
      if (next >= Number(state.admin.audit.total || 0)) return;
      state.admin.audit.offset = next;
      try {
        await loadAdminAudit();
      } catch (error) {
        setResult(el.adminAuditMeta, error.message, true);
      }
    });
  }

  if (el.adminUploadHeroImageBtn && el.adminHeroImageFile) {
    el.adminUploadHeroImageBtn.addEventListener("click", async () => {
      const file = el.adminHeroImageFile.files?.[0];
      if (!file) {
        setResult(el.adminHeroImageResult, "Выберите файл изображения.", true);
        return;
      }
      setButtonBusy(el.adminUploadHeroImageBtn, true, "Загружаем...");
      try {
        const fd = new FormData();
        fd.append("file", file);
        fd.append("kind", "hero");
        const result = await api("/api/admin/branding/upload", {
          method: "POST",
          body: fd,
          isFormData: true,
        });
        setResult(el.adminHeroImageResult, `Загружено: ${result.path || result.url || ""}`);
        if (el.heroImage) {
          el.heroImage.src = `/site-hero?v=${Date.now()}`;
        }
        showToast("Картинка шапки обновлена.");
      } catch (error) {
        setResult(el.adminHeroImageResult, error.message, true);
      } finally {
        setButtonBusy(el.adminUploadHeroImageBtn, false);
      }
    });
  }

  if (el.adminUploadFaviconBtn && el.adminFaviconFile) {
    el.adminUploadFaviconBtn.addEventListener("click", async () => {
      const file = el.adminFaviconFile.files?.[0];
      if (!file) {
        setResult(el.adminFaviconResult, "Выберите файл изображения.", true);
        return;
      }
      setButtonBusy(el.adminUploadFaviconBtn, true, "Загружаем...");
      try {
        const fd = new FormData();
        fd.append("file", file);
        fd.append("kind", "favicon");
        const result = await api("/api/admin/branding/upload", {
          method: "POST",
          body: fd,
          isFormData: true,
        });
        setResult(el.adminFaviconResult, `Загружено: ${result.path || result.url || ""}`);
        const faviconLink = document.querySelector("link[rel='icon']");
        if (faviconLink) {
          faviconLink.href = `/favicon.ico?v=${Date.now()}`;
        }
        showToast("Favicon обновлён.");
      } catch (error) {
        setResult(el.adminFaviconResult, error.message, true);
      } finally {
        setButtonBusy(el.adminUploadFaviconBtn, false);
      }
    });
  }

  el.adminExportStats.addEventListener("click", async () => {
    try {
      const scope = el.adminStatsScope.value || "all";
      const response = await fetch(`/api/admin/stats/export?scope=${encodeURIComponent(scope)}`, {
        method: "GET",
        credentials: "same-origin",
      });

      if (!response.ok) {
        throw new Error(`Экспорт не выполнен (HTTP ${response.status})`);
      }

      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `stats_${scope}.xlsx`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      setResult(el.adminStatsSummary, error.message, true);
    }
  });

  el.adminMarketSyncBtn.addEventListener("click", async () => {
    setButtonBusy(el.adminMarketSyncBtn, true, "Синхронизируем...");
    try {
      const marketToken = String(el.adminMarketSyncToken?.value || "").trim();
      const res = await api(`/api/admin/market-sync?user_id=${encodeURIComponent(state.userId)}`, {
        method: "POST",
        body: {
          user_id: state.userId,
          market_token: marketToken,
        },
      });
      setResult(el.adminStatsSummary, `Синхронизация: ${res.status}, обновлено: ${res.updated_count}.`);
      showToast(`Синхронизация завершена: ${res.updated_count} обновлено.`);
    } catch (error) {
      setResult(el.adminStatsSummary, error.message, true);
    } finally {
      setButtonBusy(el.adminMarketSyncBtn, false);
    }
  });

  el.adminImportFaqBtn.addEventListener("click", async () => {
    setButtonBusy(el.adminImportFaqBtn, true, "Импорт...");
    try {
      const file = el.adminFaqExcelFile.files?.[0];
      if (!file) throw new Error("Выберите файл FAQ Excel.");
      const fd = new FormData();
      fd.append("file", file);

      const result = await api(`/api/admin/import/faq-excel?user_id=${encodeURIComponent(state.userId)}`, {
        method: "POST",
        body: fd,
        isFormData: true,
      });
      setResult(el.adminImportResult, `FAQ импорт: ${JSON.stringify(result.result || result)}`);
      showToast("FAQ импорт завершён.");
    } catch (error) {
      setResult(el.adminImportResult, error.message, true);
    } finally {
      setButtonBusy(el.adminImportFaqBtn, false);
    }
  });

  el.adminImportFullBtn.addEventListener("click", async () => {
    setButtonBusy(el.adminImportFullBtn, true, "Импорт...");
    try {
      const file = el.adminFullExcelFile.files?.[0];
      if (!file) throw new Error("Выберите файл полного Excel.");
      const fd = new FormData();
      fd.append("file", file);

      const result = await api(`/api/admin/import/full-excel?user_id=${encodeURIComponent(state.userId)}`, {
        method: "POST",
        body: fd,
        isFormData: true,
      });
      setResult(el.adminImportResult, `Полный импорт: ${JSON.stringify(result.result || result)}`);
      showToast("Полный импорт завершён.");
    } catch (error) {
      setResult(el.adminImportResult, error.message, true);
    } finally {
      setButtonBusy(el.adminImportFullBtn, false);
    }
  });

  el.adminRunDbMigrationBtn?.addEventListener("click", async () => {
    setButtonBusy(el.adminRunDbMigrationBtn, true, "Обновляем...");
    try {
      const sourceSqlite = String(el.adminDbMigrationSource?.value || "auto").trim() || "auto";
      const truncate = Boolean(el.adminDbMigrationTruncate?.checked);
      const result = await api(`/api/admin/db/migrate?user_id=${encodeURIComponent(state.userId)}`, {
        method: "POST",
        body: {
          source_sqlite: sourceSqlite,
          truncate,
        },
      });
      const output = String(result.output || "").trim();
      const repair = result.result || {};
      const sqliteImport = repair.sqlite_import || null;
      const duration = repair.duration_ms !== undefined ? ` (${repair.duration_ms} мс)` : "";
      const sqliteSummary = sqliteImport
        ? `\nSQLite: добавлено/обновлено ${sqliteImport.upserted_rows || sqliteImport.inserted_rows || 0}, пропущено ${sqliteImport.skipped_rows || 0}.`
        : "";
      setResult(
        el.adminDbMigrationResult,
        `Миграция БД завершена${duration}.${output ? `\n${output}` : ""}${sqliteSummary}`,
        false,
      );
      showToast("Миграция БД завершена.");
    } catch (error) {
      setResult(el.adminDbMigrationResult, error.message, true);
    } finally {
      setButtonBusy(el.adminRunDbMigrationBtn, false);
    }
  });
}
function bindInitialEvents() {
  bindMainEvents();
  if (state.isAdmin) {
    bindAdminEvents();
  }
}

async function initialize() {
  try {
    bindNotificationAudioUnlock();
    if (pushNotificationsSupported()) {
      void registerNotificationServiceWorker().catch(() => {});
    }
    setupTabs();
    setupAdminTabs();
    setupAdminStoreEditorTabs();
    setupArticleEditorToolbar();
    setupArticlePhotoEditorEvents();
    startMaintenanceWatcher();
    window.addEventListener("offline", () => {
      showToast("Соединение с интернетом потеряно.", true);
    });
    window.addEventListener("online", () => {
      showToast("Соединение восстановлено.");
      void sendPresenceHeartbeat();
    });

    if (el.heroImage) {
      el.heroImage.addEventListener("error", () => {
        el.heroImage.classList.add("hidden");
      });
      el.heroImage.addEventListener("load", () => {
        el.heroImage.classList.remove("hidden");
      });
      el.heroImage.src = `/site-hero?v=${Date.now()}`;
    }

    const user = await resolveUserContext();
    state.isAuthenticated = Boolean(user.isAuthenticated);
    state.userId = Number(user.userId || 0);
    state.csrfToken = String(user.csrfToken || "");
    state.username = String(user.username || "");
    state.userEmail = String(user.email || "");
    state.userEmailVerified = Boolean(user.emailVerified);
    state.userFirstName = user.firstName || "";
    state.userLastName = user.lastName || "";
    state.userPhone = user.phone || "";
    state.userOauthProvider = user.oauthProvider || "";
    state.referralProgram = user.referralProgram || {};
    state.isAdminAccount = Boolean(user.isAdmin);
    state.isAdmin = Boolean(user.isAdmin && isAdminPage);
    if (isAdminPage && !state.isAuthenticated) {
      redirectToAuth();
      return;
    }
    startPresenceHeartbeat();
    await syncStoreCartFromServer();

    updateHeader();
    renderAccountInfo();
    el.accountOauthCard?.classList.toggle("hidden", !state.isAuthenticated);
    handleOauthLinkReturn();

    tryInitCookieNotice();
    if (state.isAuthenticated && user.requiresPdConsent) {
      showPdConsentGateForced();
      bindInitialEvents();
      return;
    }

    let bootstrap = null;
    try {
      bootstrap = await loadBootstrap();
    } catch (error) {
      renderAccessAlert(`Не удалось загрузить bootstrap: ${errorText(error)}`);
      el.welcomeText.textContent = "Ошибка загрузки данных.";
      bindInitialEvents();
      return;
    }

    if (state.isAuthenticated) {
      showPdConsentGateIfNeeded(bootstrap?.user);
    }

    bindInitialEvents();
    if (
      state.isAuthenticated
      && pushNotificationsSupported()
      && window.Notification.permission === "granted"
    ) {
      try {
        await ensurePushSubscription({ subscribeIfMissing: true });
      } catch (_pushError) {
        state.notifications.pushSyncAttempted = true;
        state.notifications.pushSubscriptionActive = false;
      }
    }
    syncNotificationPermissionPrompt();
    bindHomeSubscriptionLink();
    initCustomIngredientRows();
    if (state.isAuthenticated) {
      await loadNotifications({ silent: true });
      startNotificationsPolling();
    }

    applyHashNavigation();
    window.addEventListener("hashchange", () => applyHashNavigation());

    try {
      await loadBillingInfo();
    } catch (_e) {
      // ignore
    }

    if (state.isAuthenticated) {
      try {
        await resumePendingCardBindFlow();
      } catch (error) {
        showToast(errorText(error, "Не удалось возобновить привязку карты после возврата."), true);
      }
    }

    const jobs = [
      loadStore(),
      loadHandbook(),
      loadArticles({ selectId: articleIdFromUrl() }),
      loadEquipment(),
      loadFaq(),
      loadCategoriesAndRecipes(),
    ];
    if (state.isAuthenticated) {
      jobs.push(loadMyTickets());
    }

    await Promise.allSettled(jobs);
    openStoreProductFromUrl();

    if (state.isAdmin) {
      try {
        await refreshAdminBootstrap();
        applyRequestedAdminPanel();
      } catch (error) {
        setResult(el.adminCatalogResult, `Админка недоступна: ${error.message}`, true);
        setTabVisibility("admin", false);
        el.adminSection.classList.add("hidden");
        state.isAdmin = false;
        updateHeader();
      }
    }
  } catch (error) {
    const message = errorText(error, "Неизвестная ошибка инициализации.");
    console.error("App initialization failed:", error);
    if (el.welcomeText) {
      el.welcomeText.textContent = "Ошибка инициализации приложения.";
    }
    renderAccessAlert(`Ошибка инициализации: ${message}`);
    showToast(`Ошибка инициализации: ${message}`, true);
  }
}

if (typeof window !== "undefined") {
  window.state = state;
  window.renderCart = renderCart;
  window.updateHeader = updateHeader;
}

void initialize();
