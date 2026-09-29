/**
 * Code every module may use and none owns: security (API key, current client), web (filters, problem handler),
 * io ({@code ContentSource}) and time ({@code Clock}). It depends on no module.
 */
package com.quaysidepay.sepagateway.shared;
