This is a demonstration of how to use R3 Corda to automate the process of Tenancy Deposit protections

1. Use the central bank (http://localhost:10007/web/hotLoad/) to issue some cash
2. Transfer some cash to the "Tenant"  
3. As the landlord (http://localhost:10013/web/landlord-scheme-hotLoad/) generate a deposit request targeting the tenant (ensuring that SuperSecureDepositCo is the cash holder)
4. As the tenant (http://localhost:10010/web/tenant-hotLoad/) use your issued cash to fund this deposit
5. At some point later, as the Tenant request a refund
6. As the Landlord, choose to deduct some damages or not
7. Once you have completed the deductions, accept the refund
8. The cash will now be refunded to the tenant minus the charges, with the remainder going to the landlord. 